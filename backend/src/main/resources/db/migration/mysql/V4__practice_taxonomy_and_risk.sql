-- Adds the fine-grained taxonomy and a stable, human-readable practice id
-- (independent of the DB-generated UUID) so a review finding or a doc can
-- reference a practice by a durable code like "JAVA-EXC-001".
ALTER TABLE practice ADD COLUMN practice_code VARCHAR(40);
ALTER TABLE practice ADD COLUMN subcategory VARCHAR(60);
ALTER TABLE practice ADD COLUMN risk TEXT;

-- badExample/goodExample renamed to the clearer code/solution.
ALTER TABLE practice RENAME COLUMN bad_example TO code;
ALTER TABLE practice RENAME COLUMN good_example TO solution;

-- "language" undersold what this column holds once Hibernate, React,
-- Bootstrap, HTML/CSS, and MySQL are all in scope alongside Java/TS/JS —
-- none of those are programming languages. Rename to technology.
ALTER TABLE practice RENAME COLUMN language TO technology;
DROP INDEX idx_practice_language_active ON practice;
CREATE INDEX idx_practice_technology_active ON practice(technology, active);

-- Defensive backfill in case any row predates this column (the seeder
-- always sets practice_code for new rows).
UPDATE practice SET practice_code = CONCAT('LEGACY-', HEX(id)) WHERE practice_code IS NULL;

ALTER TABLE practice MODIFY COLUMN practice_code VARCHAR(40) NOT NULL;
CREATE UNIQUE INDEX idx_practice_code ON practice(practice_code);
CREATE INDEX idx_practice_subcategory ON practice(subcategory);
