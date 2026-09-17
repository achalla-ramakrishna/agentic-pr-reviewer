# Agentic PR Reviewer

Point it at a GitHub repository or pull request and it reviews the code:
diffs are checked against a curated knowledge base of good/bad coding
practices *and* reviewed by an LLM, the two are merged into scored findings,
and results land on a dashboard so the practices library — and the review's
accuracy — can be improved over time.

Initial target: Java full-stack applications (Spring Boot + JS/TS
frontends). Built in Java full-stack itself (Spring Boot + React/TS).

## Status

Early scaffold. See [`docs/SPEC.md`](docs/SPEC.md) for the full requirements
and the build plan broken into independently mergeable chunks, and
[`docs/architecture.md`](docs/architecture.md) for how the pieces fit
together.

## Repo layout

- `backend/` — Spring Boot 3 (Java 21) API.
- `frontend/` — React + TypeScript (Vite) dashboard.
- `docs/` — spec, architecture, ADRs.

## One-time setup

```bash
git config core.hooksPath .githooks
```

Enables the pre-commit secret scan (`.githooks/pre-commit`) so a
real-shaped credential can't land in a commit by accident.

## Running locally

Backend:

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` with an in-memory H2 database (`dev`
profile, the default). `GET /api/health` should return `{"status":"ok"}`.

To run against a real local MySQL instead (see
[`docs/adr/0002-mysql-database.md`](docs/adr/0002-mysql-database.md) for
why MySQL): create the database and user once —

```sql
CREATE DATABASE prreviewer CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'prreviewer'@'localhost' IDENTIFIED BY 'prreviewer';
GRANT ALL PRIVILEGES ON prreviewer.* TO 'prreviewer'@'localhost';
```

then run with the `mysql` profile:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Defaults to `jdbc:mysql://localhost:3306/prreviewer` with user/password
`prreviewer`/`prreviewer`; override with the `DB_URL`/`DB_USERNAME`/
`DB_PASSWORD` environment variables if yours differ.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Runs on Vite's dev server and proxies `/api/*` to `http://localhost:8080`.

See [`AGENTS.md`](AGENTS.md) for conventions and guardrails before making
changes.
