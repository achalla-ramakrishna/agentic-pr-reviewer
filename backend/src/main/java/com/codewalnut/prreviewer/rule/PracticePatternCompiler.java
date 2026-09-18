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

    public static Optional<Pattern> compile(Practice practice) {
        String detectionPattern = practice.getDetectionPattern();
        if (detectionPattern == null || detectionPattern.isBlank()) {
            return Optional.empty();
        }
        if (NON_DETERMINISTIC_PREFIXES.stream().anyMatch(detectionPattern::startsWith)) {
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
