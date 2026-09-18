package com.codewalnut.prreviewer.dto;

import com.codewalnut.prreviewer.domain.Review;
import com.codewalnut.prreviewer.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        String repoUrl,
        Integer prNumber,
        String commitSha,
        String requestedBy,
        ReviewStatus status,
        int totalFindings,
        Instant createdAt,
        Instant completedAt,
        List<FindingResponse> findings,
        ReviewSummary summary) {

    public static ReviewResponse from(Review review, List<FindingResponse> findings, ReviewSummary summary) {
        return new ReviewResponse(
                review.getId(),
                review.getRepoUrl(),
                review.getPrNumber(),
                review.getCommitSha(),
                review.getRequestedBy(),
                review.getStatus(),
                review.getTotalFindings(),
                review.getCreatedAt(),
                review.getCompletedAt(),
                findings,
                summary);
    }
}
