package com.codewalnut.prreviewer.rule;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;
import java.util.UUID;

/**
 * A single deterministic match between a changed file and a stored
 * Practice's detection pattern. Transient -- chunk 6's review orchestrator
 * turns these into persisted Finding rows (source=RULE) alongside whatever
 * the LLM pass (chunk 5) independently reports.
 */
public record RuleFinding(
        UUID practiceId,
        String practiceCode,
        Category category,
        Severity severity,
        String filePath,
        int lineStart,
        int lineEnd,
        String message) {}
