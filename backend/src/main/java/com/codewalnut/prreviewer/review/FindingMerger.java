package com.codewalnut.prreviewer.review;

import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.llm.LlmFinding;
import com.codewalnut.prreviewer.rule.RuleFinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combines the rule engine's and the LLM's independent findings for the same
 * diff into one list, marking a finding BOTH when they agree on the same
 * file, category, and a close-enough line range. That agreement is exactly
 * what gives a finding higher confidence than either source alone -- see
 * "Key point" in docs/architecture.md.
 *
 * Each rule finding is matched against at most one not-yet-matched LLM
 * finding (greedy, first match wins); everything left over on either side
 * stays RULE-only or LLM-only.
 */
public final class FindingMerger {

    /** How many lines apart two findings' ranges can be and still count as "the same spot". */
    static final int LINE_TOLERANCE = 3;

    private FindingMerger() {}

    public static List<MergedFinding> merge(
            List<RuleFinding> ruleFindings, List<LlmFinding> llmFindings, Map<String, UUID> practiceCodeToId) {
        List<MergedFinding> results = new ArrayList<>();
        Set<Integer> matchedLlmIndices = new HashSet<>();

        for (RuleFinding rule : ruleFindings) {
            int matchIndex = findMatch(rule, llmFindings, matchedLlmIndices);
            FindingSource source = FindingSource.RULE;
            if (matchIndex >= 0) {
                matchedLlmIndices.add(matchIndex);
                source = FindingSource.BOTH;
            }
            results.add(
                    new MergedFinding(
                            rule.practiceId(),
                            source,
                            rule.category(),
                            rule.severity(),
                            rule.filePath(),
                            rule.lineStart(),
                            rule.lineEnd(),
                            rule.message()));
        }

        for (int i = 0; i < llmFindings.size(); i++) {
            if (matchedLlmIndices.contains(i)) {
                continue;
            }
            LlmFinding llm = llmFindings.get(i);
            UUID practiceId = llm.practiceCode() != null ? practiceCodeToId.get(llm.practiceCode()) : null;
            results.add(
                    new MergedFinding(
                            practiceId,
                            FindingSource.LLM,
                            llm.category(),
                            llm.severity(),
                            llm.filePath(),
                            llm.lineStart(),
                            llm.lineEnd(),
                            llm.message()));
        }

        return results;
    }

    private static int findMatch(RuleFinding rule, List<LlmFinding> llmFindings, Set<Integer> alreadyMatched) {
        for (int i = 0; i < llmFindings.size(); i++) {
            if (!alreadyMatched.contains(i) && sameSpot(rule, llmFindings.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameSpot(RuleFinding rule, LlmFinding llm) {
        if (!rule.filePath().equals(llm.filePath()) || rule.category() != llm.category()) {
            return false;
        }
        if (llm.lineStart() == null || llm.lineEnd() == null) {
            return false;
        }
        return rule.lineStart() <= llm.lineEnd() + LINE_TOLERANCE
                && llm.lineStart() <= rule.lineEnd() + LINE_TOLERANCE;
    }
}
