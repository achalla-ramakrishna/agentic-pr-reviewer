package com.codewalnut.prreviewer.llm;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;

/**
 * A single finding the LLM reported for one file. Transient -- chunk 6's
 * review orchestrator turns these into persisted Finding rows (source=LLM,
 * or BOTH when a rule-engine finding independently flagged the same spot).
 *
 * practiceCode is the LLM's own claim about which retrieved Practice this
 * matches (null if it flagged something outside the provided list) --
 * unlike RuleFinding.practiceId, it isn't independently verified against the
 * database here, since the LLM only ever sees practice codes, not row ids.
 */
public record LlmFinding(
        String practiceCode,
        Category category,
        Severity severity,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String message) {}
