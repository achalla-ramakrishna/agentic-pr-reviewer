CREATE TABLE review (
    id UUID PRIMARY KEY,
    repo_url VARCHAR(500) NOT NULL,
    pr_number INTEGER,
    commit_sha VARCHAR(100),
    requested_by VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    total_findings INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
