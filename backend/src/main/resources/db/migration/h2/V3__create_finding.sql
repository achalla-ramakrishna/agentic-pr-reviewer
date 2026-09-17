CREATE TABLE finding (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES review(id) ON DELETE CASCADE,
    practice_id UUID REFERENCES practice(id),
    source VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    line_start INTEGER,
    line_end INTEGER,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    feedback_reason TEXT,
    decided_by VARCHAR(200),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_finding_review_id ON finding(review_id);
CREATE INDEX idx_finding_practice_id ON finding(practice_id);
