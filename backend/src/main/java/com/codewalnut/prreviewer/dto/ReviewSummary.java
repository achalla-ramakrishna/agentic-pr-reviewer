package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;
import java.util.Map;

/** ruleConfirmed counts source RULE + BOTH; llmOnly counts source LLM -- see SPEC's Output section. */
public record ReviewSummary(
        Map<Severity, Long> bySeverity, Map<Category, Long> byCategory, long ruleConfirmed, long llmOnly) {}
