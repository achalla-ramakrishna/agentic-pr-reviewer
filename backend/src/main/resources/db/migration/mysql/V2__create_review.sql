CREATE TABLE review (
    id BINARY(16) NOT NULL PRIMARY KEY,
    repo_url VARCHAR(500) NOT NULL,
    pr_number INT,
    commit_sha VARCHAR(100),
    requested_by VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    total_findings INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
