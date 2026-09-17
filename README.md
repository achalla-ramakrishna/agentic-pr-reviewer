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

## Running locally

Backend:

```bash
cd backend
mvn spring-boot:run
```

Runs on `http://localhost:8080` with an in-memory H2 database (dev
profile). `GET /api/health` should return `{"status":"ok"}`.

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Runs on Vite's dev server and proxies `/api/*` to `http://localhost:8080`.

See [`AGENTS.md`](AGENTS.md) for conventions and guardrails before making
changes.
