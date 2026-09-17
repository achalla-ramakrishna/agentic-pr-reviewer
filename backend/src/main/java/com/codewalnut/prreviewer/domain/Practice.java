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
 *
 * practiceCode is the stable, human-readable id (e.g. "JAVA-EXC-001") a
 * review finding or doc references — independent of the DB-generated UUID,
 * which is free to differ across environments/reseeds.
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

    @Column(name = "practice_code", nullable = false, unique = true, length = 40)
    private String practiceCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    /** The specific topic within category, e.g. "Exception Handling", "Frontend Security". */
    @Column(length = 60)
    private String subcategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Technology technology;

    /** The bad example. */
    @Column(columnDefinition = "TEXT")
    private String code;

    /** The fix. */
    @Column(columnDefinition = "TEXT")
    private String solution;

    /** Plain-English consequence if this ships, distinct from the severity label. */
    @Column(columnDefinition = "TEXT")
    private String risk;

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
