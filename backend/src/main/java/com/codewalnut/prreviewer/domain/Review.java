package com.codewalnut.prreviewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A single review run against a repo or PR. Findings (chunk 4/5/6 populate
 * these) hang off this by reviewId. Schema lands in this chunk; the GitHub
 * fetch + rule/LLM pipeline that actually fills a Review in comes later.
 */
@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    /** Null means "whole repo" mode rather than a specific PR. */
    @Column(name = "pr_number")
    private Integer prNumber;

    @Column(name = "commit_sha", length = 100)
    private String commitSha;

    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Builder.Default
    @Column(name = "total_findings", nullable = false)
    private int totalFindings = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
