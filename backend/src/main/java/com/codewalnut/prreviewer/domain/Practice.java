package com.codewalnut.prreviewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * A single curated good/bad coding practice used to deterministically compare
 * against a diff (see RuleEngineService, chunk 4) and as retrieved context for
 * the LLM review pass (see LlmReviewService, chunk 5).
 */
@Entity
@Table(name = "practice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Practice {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Language language;

    @Column(name = "bad_example", columnDefinition = "TEXT")
    private String badExample;

    @Column(name = "good_example", columnDefinition = "TEXT")
    private String goodExample;

    @Column(name = "detection_pattern", columnDefinition = "TEXT")
    private String detectionPattern;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
