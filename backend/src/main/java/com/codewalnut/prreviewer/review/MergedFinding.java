package com.codewalnut.prreviewer.review;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.Severity;
import java.util.UUID;

/**
 * One rule-engine and/or LLM finding after merging, ready to become a
 * persisted Finding row. practiceId is null when this came only from the LLM
 * flagging something outside the retrieved practice list.
 */
public record MergedFinding(
        UUID practiceId,
        FindingSource source,
        Category category,
        Severity severity,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String message) {}
