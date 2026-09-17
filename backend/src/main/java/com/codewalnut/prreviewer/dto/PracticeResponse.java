package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Language;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import java.time.Instant;
import java.util.UUID;

public record PracticeResponse(
        UUID id,
        String title,
        String description,
        Category category,
        Severity severity,
        Language language,
        String badExample,
        String goodExample,
        String detectionPattern,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static PracticeResponse from(Practice practice) {
        return new PracticeResponse(
                practice.getId(),
                practice.getTitle(),
                practice.getDescription(),
                practice.getCategory(),
                practice.getSeverity(),
                practice.getLanguage(),
                practice.getBadExample(),
                practice.getGoodExample(),
                practice.getDetectionPattern(),
                practice.isActive(),
                practice.getCreatedAt(),
                practice.getUpdatedAt());
    }
}
