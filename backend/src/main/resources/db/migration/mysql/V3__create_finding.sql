CREATE TABLE finding (
    id BINARY(16) NOT NULL PRIMARY KEY,
    review_id BINARY(16) NOT NULL,
    practice_id BINARY(16),
    source VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    line_start INT,
    line_end INT,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    feedback_reason TEXT,
    decided_by VARCHAR(200),
    decided_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_finding_review FOREIGN KEY (review_id) REFERENCES review(id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_practice FOREIGN KEY (practice_id) REFERENCES practice(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_finding_review_id ON finding(review_id);
CREATE INDEX idx_finding_practice_id ON finding(practice_id);
