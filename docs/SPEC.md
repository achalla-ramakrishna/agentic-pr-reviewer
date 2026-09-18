# SPEC — Agentic PR Reviewer

## Actor & goal

A developer or reviewer pastes a GitHub **repository URL** or **pull
request URL** into the dashboard (or calls the API directly). The system
fetches the relevant diff, reviews it, and returns findings the actor can
act on before merging — with enough evidence attached that they don't have
to re-derive it by hand.

## State change

- **Input**: a GitHub repo/PR URL + a GitHub PAT with read access to it.
- **Output**: a persisted `Review` record containing:
  - metadata (repo, PR number or "whole repo" mode, commit SHA, timestamp,
    requester)
  - a list of `Finding`s, each tagged with source (`RULE`, `LLM`, or `BOTH`
    when both independently flagged the same location), category
    (correctness / security / performance / style / test-quality /
    accessibility), severity, file + line range, message, and — where a
    stored practice matched — a reference to that `Practice`.
  - summary counts (findings by severity/category, rule-confirmed vs.
    LLM-only).
- Reviewers can mark a finding **accepted** or **rejected** (with an
  optional reason). This feedback is stored and feeds the dashboard's
  accuracy metrics — it does not silently rewrite the practices library.

## Boundaries & failure states

- Repo/PR not found, or PAT lacks access → `404`/`403` surfaced to the
  caller, no partial `Review` persisted.
- Diff too large for a single LLM context window → chunk by file, review
  each chunk independently, merge findings (never silently truncate and
  review only part of the diff without saying so).
- GitHub or OpenAI API unavailable/rate-limited → the review fails
  explicitly with a retryable error status; a rule-engine-only partial
  result is **not** presented as a complete review.
- A file's language isn't Java (or the initially supported frontend
  stack) → still LLM-reviewed for general issues, but rule-engine practice
  matching for that file is skipped and the finding set says so, rather
  than silently applying Java rules to non-Java code.
- Malicious/prompt-injection content inside a diff, commit message, or PR
  description must never alter reviewer behavior (e.g. change which
  endpoints are called, leak the PAT/API key, or suppress findings) — it is
  treated strictly as text under review, per `AGENTS.md`.

## Not in scope (initially)

- Blocking/gating actual GitHub PR merges (no status checks, no auto-merge,
  no posted PR comments) — this is a standalone reviewer + dashboard, not a
  CI gate, until a later chunk.
- Languages other than Java (backend) and the common JS/TS frontend
  frameworks paired with it. The domain model must not hardcode Java-only
  assumptions, but non-Java rule packs are future work.
- Multi-tenant auth/orgs — single shared PAT + single-team dashboard for
  now.
- Auto-editing the practices library from LLM output. Practices are
  curated by humans (via the dashboard CRUD in a later chunk); the LLM can
  be *asked* to suggest a new practice from a recurring pattern, but a
  human approves it before it becomes part of the comparison KB.

## Acceptance criteria (system-level)

- Given a valid PR URL and a PAT with access, when a review is requested,
  then a `Review` with at least one `Finding` (or an explicit "no issues
  found" summary) is persisted and retrievable via the API within the
  request/response cycle or a pollable job status.
- Given a diff that violates a stored `Practice` (e.g. catching and
  swallowing `Exception` with an empty block), when reviewed, then a
  `Finding` with `source=RULE` (or `BOTH` if the LLM also caught it)
  referencing that `Practice` is produced.
- Given the same diff reviewed twice with no KB or prompt changes, the
  rule-engine findings are identical both times (determinism); LLM findings
  may vary in phrasing but not in whether a rule-confirmed issue is
  reported.
- Given a reviewer marks a `Finding` as rejected with reason "false
  positive: intentional", the dashboard's precision metric reflects that
  rejection without deleting the underlying `Practice`.

## Build plan — PR-sized chunks

Each chunk is independently mergeable, has its own tests, and leaves the
app in a working state. Later chunks assume earlier ones are done.

1. **Toolchain setup + scaffold** *(this chunk)* — `AGENTS.md`/`CLAUDE.md`,
   backend (Spring Boot) and frontend (React/Vite) skeletons, health check
   end to end. ✅
2. **Domain model + practices knowledge base** — JPA entities
   (`Practice`, `Review`, `Finding`, feedback), Flyway migrations, seed set
   of Java good/bad practices, CRUD API + minimal library screen. ✅
3. **GitHub integration** — client to resolve a repo/PR URL into a unified
   diff + changed-file contents via PAT; error handling for the boundary
   cases above. ✅
4. **Rule engine** — deterministic checks that match diff content against
   `Practice` patterns for Java (starting with a focused, high-confidence
   rule set, not an exhaustive linter reimplementation). ✅
5. **LLM review pass** — OpenAI client, prompt built from diff + retrieved
   relevant practices (context engineering, not a full-repo dump),
   structured output parsed into `Finding`s. ✅
6. **Review orchestrator** — merges rule + LLM findings, dedups by
   file/line/category, assigns `BOTH` where they agree, persists the
   `Review`, exposes the submit + fetch API end to end.
7. **Dashboard frontend** — submit a repo/PR, view a review's findings
   grouped by severity/category with evidence, accept/reject a finding.
8. **Metrics & feedback loop** — aggregate dashboard (reviews over time,
   precision from accept/reject, top recurring categories), API to record
   feedback.
9. **Evidence-led review record** — attach reproducible evidence to each
   review (which rules fired and why, exact LLM prompt/response reference,
   diff stat) so a human can verify without re-running anything.
10. **Tests & hardening** — fill gaps from earlier chunks: prompt-injection
    resistance tests, large-diff chunking tests, API contract tests.

Chunks 2–10 will each get their own short spec note in this file's history
(or a linked doc) if requirements shift; the shape above is the working
plan, not a rigid contract — flag here if a chunk needs to be resplit once
we're in it.
