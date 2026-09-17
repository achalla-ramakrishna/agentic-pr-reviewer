package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Severity;
import com.codewalnut.prreviewer.domain.Technology;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PracticeRequest(
        @NotBlank @Size(max = 40) String practiceCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull Category category,
        String subcategory,
        @NotNull Severity severity,
        @NotNull Technology technology,
        String code,
        String solution,
        String risk,
        String detectionPattern) {}
