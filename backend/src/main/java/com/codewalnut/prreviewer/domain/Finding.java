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
 * A single review finding. practiceId is null for an LLM-only finding that
 * didn't match a stored Practice. Feedback (accept/reject) lives directly on
 * the row rather than a separate table — one live decision per finding is
 * all the spec calls for; a history table can be added later if needed.
 */
@Entity
@Table(name = "finding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "practice_id")
    private UUID practiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FindingSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(name = "line_end")
    private Integer lineEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FindingStatus status = FindingStatus.OPEN;

    @Column(name = "feedback_reason", columnDefinition = "TEXT")
    private String feedbackReason;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
