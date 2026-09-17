# ADR-0002: Switch the real database from Postgres to MySQL

- **Status**: accepted
- **Date**: 2026-09-17

## Context

ADR-0001 set up two Flyway/datasource profiles: `dev` (in-memory H2, used
by the test suite and quick local runs) and `prod` (assumed Postgres,
never actually deployed or run against a real instance). The user has a
MySQL instance already set up locally and wants to develop and test
against a real database they can inspect directly, rather than Postgres
which nobody has actually stood up.

Since nothing depended on a Postgres-specific feature (no JSONB, no
extensions), and the `prod` profile had never been exercised against a
real database, this was a clean swap rather than a migration of live
data.

## Decision

Replace the Postgres profile and dependencies with MySQL:

- `pom.xml`: `org.postgresql:postgresql` → `com.mysql:mysql-connector-j`;
  `flyway-database-postgresql` → `flyway-mysql`.
- `application.yml`: the `prod` profile is renamed `mysql` (it was never
  truly "prod" — it's just "a real database" — and `mysql` says what it
  actually is). Defaults to `jdbc:mysql://localhost:3306/prreviewer` with
  `prreviewer`/`prreviewer`, overridable via `DB_URL`/`DB_USERNAME`/
  `DB_PASSWORD`.
- `backend/src/main/resources/db/migration/postgres/` → `.../mysql/`,
  with DDL adapted for MySQL's syntax and type system:
  - UUID primary keys map to `BINARY(16)` — confirmed empirically by
    running Hibernate's own `ddl-auto=create` against a real MySQL 8.0
    instance and inspecting the generated `SHOW CREATE TABLE` output,
    rather than guessing. `boolean` fields map to `BIT(1)` and timestamps
    to `DATETIME(6)` for the same reason — matched to what Hibernate
    itself expects so `ddl-auto=validate` (used in all profiles) doesn't
    fail on a type mismatch at startup.
  - `RENAME COLUMN`/`DROP INDEX` syntax differs from Postgres/H2 (MySQL's
    `DROP INDEX` needs `ON <table>`; changing a column's nullability
    needs `MODIFY COLUMN`, not `ALTER COLUMN ... SET NOT NULL`).
  - The V4 migration's defensive UUID-to-string backfill uses `HEX(id)`
    instead of Postgres/H2's `CAST(id AS VARCHAR)`, since the column is
    binary on MySQL.

`dev` (H2, in-memory) is unchanged and remains the default for `mvn test`
and quick local runs — it doesn't require MySQL to be running at all.

## Verification

Before pushing, installed MySQL 8.0 locally and ran the actual migration
+ Hibernate validation + seeder + full CRUD cycle against it (not just
against H2), since this is exactly the kind of dialect-specific claim
that's cheap to verify and expensive to get wrong in the field:

- Probed Hibernate's own `ddl-auto=create` output against a scratch MySQL
  schema first, to get the real column types instead of guessing.
- Applied all 4 Flyway migrations against a clean MySQL database with
  `ddl-auto=validate` (the same mode every profile runs in) — passed with
  no schema mismatch.
- Confirmed the practices seeder populated all 240 rows correctly via a
  raw `SELECT COUNT(*)` against MySQL directly, not just through the API.
- Ran a full create → read → update → delete cycle through the live REST
  API against the real MySQL-backed instance.

## Consequences

- Anyone running this project now needs either Docker/a local MySQL
  install for the `mysql` profile, or can stick to `dev` (H2) for
  day-to-day work and tests, which needs nothing extra.
- Future schema changes need a new Flyway migration mirrored into both
  `h2/` and `mysql/` — the two dialects diverge more than H2/Postgres did
  (no native `UUID` type, different `ALTER TABLE` syntax), so this needs
  more care than a copy-paste, per the updated note in `AGENTS.md`.
- If a genuine Postgres deployment target shows up later, a
  `db/migration/postgres/` folder can be reintroduced the same way this
  one was built: copy the H2 migrations, adapt the few Postgres-specific
  spots, and verify against a real instance before merging.
