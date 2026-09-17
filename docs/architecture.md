# Architecture

## Repo overview

```
agentic-pr-reviewer/
  backend/   Spring Boot 3 (Java 21) API
  frontend/  React + TypeScript (Vite) dashboard
  docs/      spec, architecture, ADRs
```

## Module boundaries (backend)

```
com.codewalnut.prreviewer
  controller/   REST endpoints. No business logic — delegate to service/.
  service/      Orchestration + business rules (GitHubService,
                RuleEngineService, LlmReviewService, ReviewOrchestrator,
                PracticeService).
  client/       Outbound HTTP clients to GitHub and OpenAI. Nothing else
                talks to those APIs directly.
  repository/   Spring Data JPA repositories.
  domain/       JPA entities (Practice, Review, Finding, ReviewFeedback).
  dto/          Request/response shapes for the controller layer — never
                expose domain/ entities directly over the API.
  config/       Spring configuration (datasource, HTTP clients, CORS).
```

`controller` → `service` → (`client` and/or `repository`). `client` and
`repository` never call back up into `service`. `domain` has no dependency
on any other package.

## Data flow

```
                    ┌─────────────────────┐
  repo/PR URL  ───▶ │  GitHubService       │  fetch diff + changed files
  + PAT             │  (client/GitHubClient)│  via GitHub REST API (read-only)
                    └──────────┬──────────┘
                               │ unified diff, per file
                               ▼
              ┌────────────────────────────────┐
              │  ReviewOrchestrator             │
              │                                  │
              │   ┌──────────────┐  ┌──────────┐│
              │   │ RuleEngine    │  │ LlmReview ││
              │   │ Service       │  │ Service   ││
              │   │ (deterministic│  │ (OpenAI + ││
              │   │ Practice      │  │ retrieved ││
              │   │ pattern match)│  │ Practice  ││
              │   └──────┬───────┘  │ context)  ││
              │          │          └─────┬─────┘│
              │          ▼                ▼      │
              │      merge + dedup + confidence   │
              └──────────────┬───────────────────┘
                              ▼
                       Review + Findings
                       (persisted, MySQL/H2)
                              │
                              ▼
                    Dashboard (React) — view findings,
                    accept/reject, aggregate metrics
```

Key point: the LLM never reviews in isolation. `RuleEngineService` runs the
same `Practice` records deterministically; `ReviewOrchestrator` treats
rule-confirmed findings as higher-confidence than LLM-only ones. This is
the "store bad/good practices and compare, on top of what the LLM does" loop
from the product goal — it's what gives the review reproducible accuracy
instead of pure model variance.

## Context engineering for the LLM pass

The LLM prompt is built per review, not as a static system prompt stuffed
with the whole practices table:

1. Classify changed files by language/framework.
2. Retrieve only the `Practice` rows matching that language + the diff's
   touched categories (e.g. skip security practices for a pure CSS change).
3. Include: the diff (chunked per file if large), the retrieved practices
   as reference examples (good vs. bad snippet pairs), and a fixed output
   schema for structured findings.
4. Never include unrelated repo files, secrets, or full file trees —
   least-context, not most-context.

## Token/cost notes

Routine, high-volume calls (e.g. re-running a review after a feedback
change) should default to a cheaper/faster model where the task is simple
classification or re-scoring; the full review pass on a new PR uses the
configured frontier model. This gets tuned in chunk 5/8 once real usage
data exists — flagging it here so it isn't forgotten (see guidebook
competency 10, Token Economics).

## Extending beyond Java

`Practice.language` and `Finding` are modeled as free-form/enum-extensible
fields, not a Java-only schema, so adding a new language later is a new set
of seeded `Practice` rows + rule-matchers, not a schema migration.
