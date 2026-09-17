# ADR-0001: Initial architecture — stack, LLM provider, GitHub access

- **Status**: accepted
- **Date**: 2026-09-17

## Context

Building an agentic PR/repo reviewer that: (1) fetches a GitHub repo or PR,
(2) compares the diff against a stored knowledge base of good/bad coding
practices, (3) augments that with an LLM review pass, (4) merges both into
findings, and (5) shows results + accuracy metrics on a dashboard. Initial
target is Java full-stack codebases. Four decisions were needed before
writing code: the reviewer app's own stack, whether "Java full-stack" also
describes the app itself, the LLM provider, and how the app authenticates
to GitHub.

## Decisions

1. **App stack: Java full-stack (Spring Boot 3 + React/TypeScript).**
   Chosen over a Node/TS or Python backend because the team is most fluent
   here, and building the reviewer in the same stack it initially
   specializes in reviewing makes it easy to dogfood — we can point it at
   its own PRs.
2. **Scope: "Java full-stack" describes both the target codebases reviewed
   *and* the reviewer app's own implementation stack.**
3. **LLM provider: OpenAI API** (`OPENAI_API_KEY`, default model
   `gpt-4o`, configurable in `application.yml`). The `LlmReviewService` is
   the only place that knows about OpenAI specifically — if a second
   provider is needed later, that's the seam to add an interface at,
   without redesigning the orchestrator.
4. **GitHub access: user-supplied Personal Access Token**, sent per
   request (not stored server-side long-term in this chunk), scoped to
   read-only repo access. Simpler to ship than a GitHub App (no webhook
   infra, no org install flow) and sufficient for a single team using the
   dashboard directly. Revisit as a GitHub App if/when automatic
   PR-triggered review (webhook-driven, per guidebook's "Remote/Autonomous
   era") is needed.

## Alternatives considered

- **Node/TS or Python backend** — rejected for now per team stack fit
  (decision 1); the module boundaries (client/service/repository) are kept
  provider-agnostic enough that porting is possible later, just not free.
- **GitHub App with webhooks** — more powerful (auto-review on PR open,
  posts inline PR comments) but real upfront cost: app manifest, webhook
  receiver, org installation flow, install-token exchange. Deferred to a
  later chunk once the core review loop is proven.
- **Multi-provider LLM abstraction from day one** — deferred; premature
  given a single provider is confirmed. The `LlmReviewService` boundary
  keeps this cheap to add later without becoming a leaky abstraction now.

## Consequences

- The PAT the user pastes into the dashboard must be handled carefully:
  never logged, never persisted in plaintext beyond the request lifecycle
  in this chunk (see `AGENTS.md` guardrails). A later chunk should revisit
  whether/how to store it (e.g. encrypted, scoped) if recurring reviews
  without re-pasting a token are wanted.
- Because rule-engine findings and LLM findings are both first-class and
  merged (not LLM-only), the domain model needs a `source` field on
  `Finding` from the start (chunk 2) — retrofitting it later would touch
  every persisted row.
- Java-only assumptions must stay out of the domain schema even though
  Java is the only supported language today (see `architecture.md`,
  "Extending beyond Java").
