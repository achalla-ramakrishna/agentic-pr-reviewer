package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Language;
import com.codewalnut.prreviewer.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PracticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull Category category,
        @NotNull Severity severity,
        @NotNull Language language,
        String badExample,
        String goodExample,
        String detectionPattern) {}
