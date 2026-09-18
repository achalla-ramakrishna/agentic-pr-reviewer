package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Finding;
import com.codewalnut.prreviewer.domain.FindingSource;
import com.codewalnut.prreviewer.domain.FindingStatus;
import com.codewalnut.prreviewer.domain.Severity;
import java.time.Instant;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        UUID practiceId,
        String practiceCode,
        String practiceTitle,
        FindingSource source,
        Category category,
        Severity severity,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String message,
        FindingStatus status,
        String feedbackReason,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt) {

    public static FindingResponse from(Finding finding, String practiceCode, String practiceTitle) {
        return new FindingResponse(
                finding.getId(),
                finding.getPracticeId(),
                practiceCode,
                practiceTitle,
                finding.getSource(),
                finding.getCategory(),
                finding.getSeverity(),
                finding.getFilePath(),
                finding.getLineStart(),
                finding.getLineEnd(),
                finding.getMessage(),
                finding.getStatus(),
                finding.getFeedbackReason(),
                finding.getDecidedBy(),
                finding.getDecidedAt(),
                finding.getCreatedAt());
    }
}
