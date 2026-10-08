# Job Description — Senior Java Engineer (Airline Platforms)

**Company:** Sabre Corporation
**Team:** Airline Solutions — reservations, inventory and distribution platforms
**Level:** Senior Software Engineer (typically 6–10 years)
**Location:** _[City / Hybrid / Remote — fill in]_
**Employment type:** Full-time

> Draft for the hiring manager. Edit the bracketed fields, and check the company
> boilerplate and benefits wording with Talent Acquisition before you post it.

---

## About the role

Airlines run their business on our platforms: shopping, booking, ticketing,
inventory and distribution, for millions of travellers every day. As a Senior
Java Engineer you'll design, build and run high-throughput, low-latency
services on Java 17 and Spring Boot. These services expose REST and gRPC APIs,
persist to MongoDB, and ship through automated CI/CD with strict quality gates.

You'll own features end to end, from clarifying an ambiguous airline requirement
to running the service in production. We're looking for someone who works
independently, asks the questions that uncover the real requirement, and leaves
every codebase better than they found it.

## What you'll do

- **Design and build microservices** in Java 17 / Spring Boot 3 for airline
  domains such as flight availability, reservations (PNRs), seat inventory, fares
  and schedule changes.
- **Expose APIs** as Spring REST (JSON, OpenAPI) for web and mobile channels, and
  as **gRPC / Protocol Buffers** for high-volume service-to-service and partner
  traffic. You'll manage channels, stubs, deadlines and backward-compatible
  schema evolution.
- **Model and query data in MongoDB.** That means document design (embed vs.
  reference), filters, atomic updates and update operators, aggregation pipelines,
  indexing strategy, and diagnosing slow queries with `explain()`.
- **Secure services** with Spring Security and **JWT**: stateless authentication,
  role- and ownership-based authorization, and secure handling of secrets and PII.
- **Make services observable and resilient.** Write structured **SLF4J** logging
  with correlation IDs, build servlet filters and interceptors, design a consistent
  exception-handling and error-contract strategy, and add **caching** with the
  right invalidation.
- **Write tests that matter.** Use **JUnit 5** and Mockito for unit tests,
  Spring test slices, Testcontainers for integration tests, **Spock/Groovy** for
  data-driven specifications, and **Playwright** for end-to-end tests with the
  **React** front end.
- **Own the pipeline.** Maintain **Jenkins** declarative pipelines and shared
  libraries in **Groovy**, keep **SonarQube** quality gates green, and **tune
  Sonar rules and quality profiles** as code.
- **Contribute to system design.** Review designs, write ADRs, and weigh
  consistency, latency, cost and operability. Mentor engineers and raise the bar in
  code reviews.
- **Work across functions** with product owners, airline SMEs, QA and SRE.
  Turn vague asks ("make rebooking faster") into crisp, testable requirements.

## What you bring (must-have)

- **Strong core Java.** You can explain how collections, generics,
  immutability, `equals`/`hashCode`, concurrency basics (executors, `CompletableFuture`,
  thread safety) and the JVM memory model work, and you use them correctly.
- **Java 17 fluency.** You use records, sealed types, switch expressions, pattern
  matching for `instanceof`, text blocks and `var` with good judgement.
- **Streams and lambdas.** You can write and read collectors (`groupingBy`,
  `partitioningBy`, `toMap` with merge functions), functional interfaces and
  method references. You know when a loop is clearer.
- **SOLID and clean design.** You can point to examples in your own code (strategy
  over `if/else` ladders, dependency inversion, small cohesive classes) and explain
  the trade-offs.
- **Exception handling.** You build domain exception hierarchies, never swallow
  exceptions, handle `InterruptedException` correctly, and map errors to clear API
  contracts (e.g. RFC 9457 Problem Details).
- **Logging with SLF4J/Logback.** You use parameterised messages, choose log
  levels deliberately, use MDC/correlation IDs, and never log secrets or PII.
- **Spring Boot 3, Spring MVC REST and Spring Security**, including filter
  chains, JWT validation, method security and CORS.
- **MongoDB in production.** You know data modelling, CRUD, query operators,
  update operators (`$set`, `$inc`, positional `$`, `arrayFilters`), aggregation,
  compound/partial/TTL indexes, and Spring Data MongoDB (`MongoRepository`,
  `MongoTemplate`, `Criteria`).
- **gRPC and Protocol Buffers.** You can write `.proto` contracts, generate stubs,
  use unary and streaming RPCs, interceptors and status codes, and evolve schemas
  without breaking clients.
- **Caching.** Spring Cache abstraction, Caffeine and/or Redis, TTL and
  eviction strategy, and the staleness trade-offs that come with them.
- **Testing.** JUnit 5 and Mockito, Spring test slices, and integration tests
  against real dependencies.
- **CI/CD with Jenkins** (declarative pipelines, Groovy) and **SonarQube**,
  including updating rules, quality profiles and quality gates.
- **Tooling.** IntelliJ IDEA, Git, Maven/Gradle, **Postman** and/or **Bruno**
  for API work, and Tomcat (embedded and standalone).
- **Ownership.** You're self-motivated, deliver independently, and communicate
  clearly in writing.

## Nice to have

- Airline or travel domain experience: PNR / reservations, inventory and
  availability, fares and pricing, ticketing / EMDs, schedule changes, NDC, or
  GDS distribution.
- Spock framework (Groovy) for specifications, and Groovy beyond pipelines.
- Playwright for end-to-end testing, and working knowledge of **React** /
  TypeScript to debug across the stack.
- Docker, Kubernetes, Helm; a cloud platform (GCP / AWS / Azure).
- Messaging (Kafka / Pub/Sub), MongoDB change streams, transactions, sharding.
- Observability: OpenTelemetry, Prometheus/Grafana, ELK/Splunk/Dynatrace.

## How we work

- Small, reviewed pull requests; trunk-based development; quality gates that
  block, not just warn.
- "You build it, you run it": engineers take part in on-call for the services they own.
- Decisions are written down (ADRs) and are open to challenge.

## Hiring process (suggested)

1. **Recruiter screen** (30 min): motivation, logistics.
2. **Technical screen** (60 min): core Java, Java 17, streams live exercise.
3. **System design** (60 min): e.g. "Design seat inventory for a flash sale
   without overselling." See the interview kit below.
4. **Hands-on pairing** (90 min): extend a small Spring Boot + MongoDB service
   (add an endpoint, a filter and a test). The SkyRoute app in this repo works as the base.
5. **Hiring manager / values** (45 min): ownership, ambiguity, asking the right
   questions.

---

## Interviewer kit

### Screening questions with what a strong answer includes

| Area | Question | Strong answer mentions |
|---|---|---|
| Core Java | Why must `equals` and `hashCode` be overridden together? What breaks if they aren't? | HashMap/HashSet buckets, contract, records generate both |
| Java 17 | When would you use a sealed interface? | Closed domain hierarchies, exhaustive `switch`, compiler-checked |
| Streams | Group bookings by route and sum revenue. | `groupingBy(route, reducing/summingX)`, BigDecimal not double, `toMap` merge |
| Lambdas | What's "effectively final" and why does it matter? | Captured variables, thread safety, no mutation inside lambdas |
| SOLID | Show OCP in code you've written. | Strategy/registry instead of if-else, new behaviour = new class |
| Exceptions | Checked vs unchecked; what do you do with `InterruptedException`? | Restore the interrupt flag, never swallow, translate at boundaries |
| Logging | What's wrong with `log.info("User " + user + " logged in")`? | Eager concatenation, use `{}`, PII risk, MDC for context |
| Spring Security | Walk through how a JWT request is authenticated. | Filter chain, `OncePerRequestFilter`, SecurityContext, stateless, signature + `exp` checks |
| MongoDB | Two agents try to buy the last seat at the same time. How does your update prevent overselling? | Single atomic `updateOne` with condition (`$elemMatch` + `$gte`) and `$inc`; check `modifiedCount` |
| MongoDB | Embed or reference: passengers in a booking? Bookings in a flight? | Embed bounded and owned data; reference unbounded data (16 MB doc limit) |
| MongoDB | A query is slow. What do you do? | `explain("executionStats")`, COLLSCAN vs IXSCAN, ESR rule, docsExamined/nReturned |
| gRPC | How do you add a field without breaking old clients? | New field number, never reuse or renumber, `reserved`, defaults |
| Caching | You cache flight search. What goes wrong? | Stale seat counts, short TTL plus eviction on write, distributed cache for multiple nodes |
| CI/CD | The Sonar quality gate fails on "coverage on new code". What do you do? | Add tests; don't lower the gate; adjust rules only via a reviewed profile change |
| Testing | When do you use a mock and when Testcontainers? | Unit logic vs DB semantics (operators, indexes, transactions) |

### System design prompt (60 min)

> *"Design the seat-inventory and booking service for an airline running a
> flash sale: 50k booking attempts per minute on 200 flights. A seat must never
> be sold twice, and a booking can span multiple flights."*

What to look for:

- Clarifying questions first: peak versus average load, consistency needs,
  hold expiry, payment flow, multi-segment rules.
- An atomic conditional decrement per flight document, and a compensation
  (saga) or transaction for multi-flight bookings.
- Idempotency keys for retries, timed seat holds (TTL), outbox and events for
  downstream systems.
- Cache read paths (search) but never the write path. Explain the staleness trade-off.
- Hot-document contention: shard or split inventory per cabin or bucket if needed.
- Observability: correlation IDs, SLOs, alerts on oversell and compensation failures.

### Scorecard (1–4 each)

Core Java & Java 17 · Streams/Lambdas · Design (SOLID) · Spring/Security ·
MongoDB · APIs (REST/gRPC) · Testing · CI/CD & Quality · System Design ·
Communication & Ownership
