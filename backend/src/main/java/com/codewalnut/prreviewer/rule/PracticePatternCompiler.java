package com.codewalnut.prreviewer.rule;

import com.codewalnut.prreviewer.domain.Practice;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles a Practice's detectionPattern into a regex, or reports that none
 * is usable. A majority of the seeded practices carry a "NO_CLEAN_REGEX:" /
 * "NO_ANNOTATION_HEURISTIC:" / "NO_ASSERTION_HEURISTIC:" sentinel instead of
 * an actual pattern -- curated admissions that the check needs type
 * information, control flow, or cross-file context a regex can't see. Those
 * are intentionally left for the LLM review pass (chunk 5) rather than
 * forced into a pattern that would either fail to compile or silently match
 * the wrong thing.
 */
public final class PracticePatternCompiler {

    private static final Logger log = LoggerFactory.getLogger(PracticePatternCompiler.class);

    private static final Set<String> NON_DETERMINISTIC_PREFIXES =
            Set.of("NO_CLEAN_REGEX:", "NO_ANNOTATION_HEURISTIC:", "NO_ASSERTION_HEURISTIC:");

    private PracticePatternCompiler() {}

    /**
     * True when this practice carries one of the sentinel prefixes admitting
     * no regex can safely check it -- exactly the practices the LLM review
     * pass (chunk 5) retrieves as context, since the rule engine skips them.
     */
    public static boolean isNonDeterministic(Practice practice) {
        String detectionPattern = practice.getDetectionPattern();
        return detectionPattern != null
                && NON_DETERMINISTIC_PREFIXES.stream().anyMatch(detectionPattern::startsWith);
    }

    public static Optional<Pattern> compile(Practice practice) {
        String detectionPattern = practice.getDetectionPattern();
        if (detectionPattern == null || detectionPattern.isBlank()) {
            return Optional.empty();
        }
        if (isNonDeterministic(practice)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Pattern.compile(detectionPattern));
        } catch (PatternSyntaxException e) {
            log.warn(
                    "Practice {} has an invalid detectionPattern, skipping rule-engine check: {}",
                    practice.getPracticeCode(),
                    e.getMessage());
            return Optional.empty();
        }
    }
}
