package com.codewalnut.prreviewer.service;

import com.codewalnut.prreviewer.client.ChangedFile;
import com.codewalnut.prreviewer.client.ChangedFileStatus;
import com.codewalnut.prreviewer.client.GitHubDiff;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Technology;
import com.codewalnut.prreviewer.repository.PracticeRepository;
import com.codewalnut.prreviewer.rule.FileTechnologyMapper;
import com.codewalnut.prreviewer.rule.PracticePatternCompiler;
import com.codewalnut.prreviewer.rule.RuleFinding;
import com.codewalnut.prreviewer.rule.UnifiedDiffReconstructor;
import com.codewalnut.prreviewer.rule.UnifiedDiffReconstructor.Reconstructed;
import com.codewalnut.prreviewer.rule.UnifiedDiffReconstructor.ReconstructedLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Deterministically checks each changed file in a GitHubDiff against the
 * subset of stored Practice rows whose detectionPattern is an actual regex
 * (see PracticePatternCompiler for the ones that aren't). Runs entirely
 * in-process against already-fetched diff/file text -- no LLM call, no
 * network -- so results are reproducible given the same diff and the same
 * active practices.
 *
 * Not an exhaustive linter reimplementation: it's the "focused, high
 * confidence" layer the spec calls for. Everything a regex can't safely
 * decide (needs type info, control flow, or cross-file context) is left to
 * the LLM pass in chunk 5.
 */
@Service
public class RuleEngineService {

    /** Safety valve against a pathological file matching the same practice repeatedly. */
    static final int MAX_MATCHES_PER_FILE_PER_PRACTICE = 25;

    private final PracticeRepository practiceRepository;

    public RuleEngineService(PracticeRepository practiceRepository) {
        this.practiceRepository = practiceRepository;
    }

    public List<RuleFinding> review(GitHubDiff diff) {
        List<RuleFinding> findings = new ArrayList<>();
        Map<Technology, List<CompiledPractice>> compiledByTechnology = new EnumMap<>(Technology.class);

        for (ChangedFile file : diff.files()) {
            if (file.status() == ChangedFileStatus.REMOVED) {
                continue;
            }
            Reconstructed reconstructed = reconstruct(file);
            if (reconstructed == null || reconstructed.lines().isEmpty()) {
                continue;
            }

            for (Technology technology : FileTechnologyMapper.technologiesFor(file.path())) {
                List<CompiledPractice> compiledPractices =
                        compiledByTechnology.computeIfAbsent(technology, this::loadAndCompile);
                for (CompiledPractice compiledPractice : compiledPractices) {
                    findings.addAll(matchAll(compiledPractice, file.path(), reconstructed));
                }
            }
        }
        return findings;
    }

    private Reconstructed reconstruct(ChangedFile file) {
        if (file.content() != null) {
            return UnifiedDiffReconstructor.fromWholeFileContent(file.content());
        }
        if (file.patch() != null) {
            return UnifiedDiffReconstructor.fromPatch(file.patch());
        }
        return null;
    }

    private List<CompiledPractice> loadAndCompile(Technology technology) {
        List<CompiledPractice> compiled = new ArrayList<>();
        for (Practice practice : practiceRepository.findByTechnologyAndActiveTrue(technology)) {
            PracticePatternCompiler.compile(practice)
                    .ifPresent(pattern -> compiled.add(new CompiledPractice(practice, pattern)));
        }
        return compiled;
    }

    private List<RuleFinding> matchAll(
            CompiledPractice compiledPractice, String filePath, Reconstructed reconstructed) {
        List<RuleFinding> results = new ArrayList<>();
        Matcher matcher = compiledPractice.pattern().matcher(reconstructed.text());

        int fromIndex = 0;
        int matchCount = 0;
        while (matchCount < MAX_MATCHES_PER_FILE_PER_PRACTICE
                && fromIndex <= reconstructed.text().length()
                && matcher.find(fromIndex)) {
            int start = matcher.start();
            int end = matcher.end();
            int lastOffset = end > start ? end - 1 : start;
            fromIndex = end > start ? end : end + 1;

            ReconstructedLine startLine = reconstructed.lineAt(start);
            ReconstructedLine endLine = reconstructed.lineAt(lastOffset);

            if (reconstructed.anyAddedBetween(startLine.newLineNumber(), endLine.newLineNumber())) {
                Practice practice = compiledPractice.practice();
                results.add(
                        new RuleFinding(
                                practice.getId(),
                                practice.getPracticeCode(),
                                practice.getCategory(),
                                practice.getSeverity(),
                                filePath,
                                startLine.newLineNumber(),
                                endLine.newLineNumber(),
                                practice.getTitle()));
                matchCount++;
            }
        }
        return results;
    }

    private record CompiledPractice(Practice practice, Pattern pattern) {}
}
