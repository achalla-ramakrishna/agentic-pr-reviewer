package com.codewalnut.prreviewer.dto;

import jakarta.validation.constraints.NotBlank;

public record GitHubDiffRequest(
        @NotBlank String url,
        /** GitHub PAT with read access. Omit for a public repo. Never logged. */
        String token) {}
