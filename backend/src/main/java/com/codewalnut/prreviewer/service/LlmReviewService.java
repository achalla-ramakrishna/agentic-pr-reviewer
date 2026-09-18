package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.client.OpenAiClient;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.llm.LlmResponseParser;
import com.codewalnut.prreviewer.llm.PracticeContextSelector;
import com.codewalnut.prreviewer.llm.ReviewPromptBuilder;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.rule.FileTechnologyMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Runs the LLM review pass: one OpenAI call per eligible changed file, each
 * prompt scoped to that file's content/diff plus only the Practice rows the
 * rule engine can't check deterministically (see PracticeContextSelector) --
 * never a whole-repo dump, per AGENTS.md.
 *
 * Never reviews in isolation: RuleEngineService (chunk 4) independently
 * checks the same Practice table against the same diff; chunk 6's
 * orchestrator combines both into scored, deduplicated findings.
 *
 * If OpenAI becomes unavailable partway through, the exception propagates
 * rather than being swallowed -- per SPEC, a partial LLM pass must never be
 * presented as a complete review.
 */
@Service
public class LlmReviewService {

    private final PracticeRepository practiceRepository;
    private final OpenAiClient openAiClient;

    public LlmReviewService(PracticeRepository practiceRepository, OpenAiClient openAiClient) {
        this.practiceRepository = practiceRepository;
        this.openAiClient = openAiClient;
    }

    public List<LlmFinding> review(GitHubDiff diff) {
        List<LlmFinding> findings = new ArrayList<>();
        for (ChangedFile file : diff.files()) {
            if (file.status() == ChangedFileStatus.REMOVED) {
                continue;
            }
            if (file.content() == null && file.patch() == null) {
                continue;
            }

            List<Practice> practices = retrievePractices(file.path());
            String rawResponse =
                    openAiClient.chatCompletion(
                            ReviewPromptBuilder.systemPrompt(), ReviewPromptBuilder.userPrompt(file, practices));
            findings.addAll(LlmResponseParser.parse(rawResponse, file.path()));
        }
        return findings;
    }

    private List<Practice> retrievePractices(String filePath) {
        Set<Technology> technologies = FileTechnologyMapper.technologiesFor(filePath);
        List<Practice> candidates = new ArrayList<>();
        for (Technology technology : technologies) {
            candidates.addAll(practiceRepository.findByTechnologyAndActiveTrue(technology));
        }
        return PracticeContextSelector.select(candidates);
    }
}
