CREATE TABLE practice (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    language VARCHAR(30) NOT NULL,
    bad_example TEXT,
    good_example TEXT,
    detection_pattern TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_practice_language_active ON practice(language, active);
