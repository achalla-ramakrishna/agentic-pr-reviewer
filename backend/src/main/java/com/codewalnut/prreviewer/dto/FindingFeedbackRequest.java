package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.FindingStatus;
import jakarta.validation.constraints.NotNull;

/** A reviewer's accept/reject decision on a finding (or reopening one back to OPEN). */
public record FindingFeedbackRequest(@NotNull FindingStatus status, String feedbackReason, String decidedBy) {}
