package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Practice;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import java.time.Instant;
import java.util.UUID;

public record PracticeResponse(
        UUID id,
        String practiceCode,
        String title,
        String description,
        Category category,
        String subcategory,
        Severity severity,
        Technology technology,
        String code,
        String solution,
        String risk,
        String detectionPattern,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static PracticeResponse from(Practice practice) {
        return new PracticeResponse(
                practice.getId(),
                practice.getPracticeCode(),
                practice.getTitle(),
                practice.getDescription(),
                practice.getCategory(),
                practice.getSubcategory(),
                practice.getSeverity(),
                practice.getTechnology(),
                practice.getCode(),
                practice.getSolution(),
                practice.getRisk(),
                practice.getDetectionPattern(),
                practice.isActive(),
                practice.getCreatedAt(),
                practice.getUpdatedAt());
    }
}
