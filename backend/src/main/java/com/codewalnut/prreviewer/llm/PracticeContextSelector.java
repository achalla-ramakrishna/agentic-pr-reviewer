package com.codewalnut.prreviewer.llm;

import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.rule.PracticePatternCompiler;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Picks which retrieved Practice rows are worth spending prompt context on.
 * Only practices the rule engine (chunk 4) can't check deterministically are
 * included -- feeding the LLM a practice already covered by a reliable regex
 * would spend context on a case already handled with reproducible accuracy,
 * against the "least-context, not most-context" principle in
 * docs/architecture.md.
 */
public final class PracticeContextSelector {

    /** Hard cap on how many practices go into one file's prompt. */
    static final int MAX_PRACTICES_PER_FILE = 30;

    private PracticeContextSelector() {}

    public static List<Practice> select(List<Practice> candidatePractices) {
        return candidatePractices.stream()
                .filter(PracticePatternCompiler::isNonDeterministic)
                .sorted(Comparator.comparing(Practice::getPracticeCode))
                .limit(MAX_PRACTICES_PER_FILE)
                .collect(Collectors.toList());
    }
}
