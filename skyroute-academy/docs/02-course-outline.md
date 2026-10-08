# Course Outline — Senior Java Airline Engineer, Hands-On

**Goal:** by the end you can build, test, secure, ship and explain every part
of SkyRoute, the airline reservation app in this folder. You'll also be able to
interview senior candidates on the same topics.

**Format:** 14 modules, about 10–12 weeks at 8–10 hours/week. Each module has
four parts:

1. **Learn**: the concepts, with the manual section to read.
2. **Read the code**: where SkyRoute already does it.
3. **Lab**: something you build or break yourself.
4. **Checkpoint**: what you must be able to explain out loud without notes.

> Tip: create a branch per module (`git switch -c module-03`) and commit your
> lab work. Your git history becomes your learning journal.

---

## Module 0 — Setup and the airline domain (½ week)

**Learn**
- Toolchain: JDK 17 (Temurin), IntelliJ IDEA, Maven, Docker Desktop, Node 20,
  Bruno and/or Postman, `mongosh`, MongoDB Compass.
- IntelliJ essentials: the Run/Debug configs for Spring Boot, the HTTP client,
  the database tool window for MongoDB, the SonarLint plugin, and the shortcuts
  for refactoring (Extract Method/Variable, Change Signature, Inline).
- Airline vocabulary: PNR / record locator, segment, leg, cabin vs booking class,
  inventory, availability, fare, ticket, GDS, NDC, PSS, load factor, minimum
  connection time. (Manual §1.)

**Lab**
- `docker compose up -d` → open http://localhost:8081 (mongo-express) and browse
  `flights`, `bookings`, `users`.
- Import the Bruno collection (`api-clients/bruno/SkyRoute`), run **Login as
  agent → Search flights → Create booking → Get booking**.

**Checkpoint:** explain a PNR, a segment, and why a lap infant doesn't
decrement seat inventory.

---

## Module 1 — Core Java and Java 17 foundations (1 week)

**Learn**
- OOP done right: encapsulation, immutability, composition over inheritance.
- `equals`/`hashCode`/`compareTo` contracts; `Comparable` vs `Comparator`.
- Collections: `List/Set/Map` implementations and their complexity; `EnumMap`,
  `TreeMap`, unmodifiable vs immutable copies (`List.copyOf`).
- Generics: bounded types, `? extends` / `? super` (PECS).
- Java 9–17 features: `var`, records, sealed classes, switch expressions, text
  blocks, pattern matching for `instanceof`, `Stream.toList()`, helpful NPEs.
- `BigDecimal` for money: scale, `RoundingMode.HALF_EVEN`, `compareTo` vs `equals`.

**Read the code**
- Records as DTOs and value objects: `FlightSearchCriteria` (compact canonical
  constructor that normalises input), `CabinInventory`, `Passenger`.
- Enums with behaviour: `PassengerType.occupiesSeat()`, `FlightStatus.isSellable()`.
- Switch expressions: `FlightService.comparatorFor`, `FlightAvailabilityGrpcService.toCabin`.
- Money: `FareRules` (BigDecimal + HALF_EVEN).

**Lab**
- Add a `YTH` (youth, 90%) passenger type. Notice how little you change; Module 3 explains why.
- In `StreamsAndLambdasKataTest`, finish `ex7` with a sealed `Fare` hierarchy.
- Break `equals` on a record-like class you write by hand and watch a `HashSet` misbehave.

**Checkpoint:** why `new BigDecimal("1.10").equals(new BigDecimal("1.1"))` is false,
and which comparison you use in tests.

---

## Module 2 — Streams and lambdas mastery (1 week)

**Learn**
- Functional interfaces: `Function`, `BiFunction`, `Predicate`, `Supplier`,
  `Consumer`, `UnaryOperator`; composition (`andThen`, `compose`, `and/or/negate`).
- Method references (the four kinds). Effectively-final capture.
- Stream pipeline: source → intermediate (lazy) → terminal. Stateless vs stateful ops.
- Collectors: `toList`, `toMap` (with merge + map supplier), `groupingBy`
  (downstream collectors), `partitioningBy`, `joining`, `teeing`, `summingX/averagingX`.
- `Optional`: `map/flatMap/orElseGet/orElseThrow`; when *not* to use it
  (fields, parameters).
- `flatMap` (including `Optional.stream()`); primitive streams; `reduce`.
- Pitfalls: side effects in lambdas, parallel streams on shared state, a
  checked exception inside a lambda.

**Read the code**
- `foundations/StreamsAndLambdasKataTest.java`: 8 exercises with solutions.
- `FlightService.search`: `flatMap` + `Optional.stream()` + `sorted` + `toList()`.
- `BookingService.validatePassengerMix`: `groupingBy(..., counting())`.
- `FarePricingService` constructor: `toMap` into an `EnumMap` with a merge
  function that throws on duplicates.

**Lab**
- Delete each kata solution and re-implement it from memory until all 8 are green.
- Write a stream that returns the top 3 routes by total seats sold, as a
  `LinkedHashMap<String, Integer>`.

**Checkpoint:** explain lazy evaluation using `peek()`, and when a plain `for`
loop is the better choice.

---

## Module 3 — SOLID and clean design (1 week)

**Learn**
- **S**ingle Responsibility, **O**pen/Closed, **L**iskov, **I**nterface
  Segregation, **D**ependency Inversion. Each with a "smell" and a "fix".
- Patterns that make SOLID concrete: Strategy, Template Method, Factory,
  Adapter, Decorator.
- Package-by-feature vs package-by-layer. Constructor injection only.
- Rich vs anaemic domain models: put state transitions on the entity.

**Read the code** (Manual §4 has the full map)
- SRP: `FlightSearchQueryBuilder` is separate from `FlightService`.
- OCP + DIP: `PassengerFareRule` strategies injected as a `List` into `FarePricingService`.
- Adapter: `FlightAvailabilityGrpcService` adapts gRPC to the same `FlightService`.
- Rich domain: `Booking.cancel()` enforces its own rules.

**Lab**
- Add a "corporate discount" fare rule without editing `FarePricingService`.
- Find one SOLID violation in your own team's code and write a 1-page refactoring proposal.

**Checkpoint:** whiteboard the fare-rule design and explain which principle
each part satisfies.

---

## Module 4 — Exception handling and logging with SLF4J (1 week)

**Learn**
- Checked vs unchecked; when to translate exceptions; exception hierarchies.
- Never swallow; log *or* rethrow (not both); keep the cause.
  `InterruptedException` → restore the flag.
- Error contracts: RFC 9457 Problem Details, stable `errorCode`s, no stack
  traces to clients, correct HTTP status codes.
- SLF4J API vs Logback implementation; parameterised logging; levels
  (ERROR/WARN/INFO/DEBUG/TRACE) and what each is for.
- MDC and correlation IDs; async appenders; rolling policies; never log secrets or PII.
- Spring profiles in `logback-spring.xml`.

**Read the code**
- `common/error/*`: `SkyRouteException` hierarchy and `GlobalExceptionHandler`.
- `CorrelationIdFilter` (MDC + header validation) and `RequestLoggingFilter`.
- `BookingService.releaseQuietly`: logs a compensation failure without masking the original error.
- `GrpcServerLifecycle.stop`: correct `InterruptedException` handling.
- `logback-spring.xml`.

**Lab**
- Add `PaymentDeclinedException` (HTTP 402) and a test that asserts its Problem Detail JSON.
- Trigger an error, grab `correlationId` from the response, and find every log line for that request.
- Switch the console to JSON logs (`logstash-logback-encoder`).

**Checkpoint:** what does a support engineer see on a 500 response, and what
do we log?

---

## Module 5 — Spring Boot fundamentals and REST APIs on Tomcat (1 week)

**Learn**
- Auto-configuration, starters, `@ConfigurationProperties` (validated, type-safe),
  profiles, externalised config.
- IoC/DI: bean scopes, lifecycle (`SmartLifecycle`, `ApplicationRunner`), conditional beans.
- Spring MVC: controllers, request binding (path/query/body), Bean Validation
  (`@Valid`, `@Validated`), `ResponseEntity`, status codes, `Location` headers.
- REST design: resources, verbs, idempotency, pagination, versioning (`/api/v1`).
- Embedded Tomcat (threads, graceful shutdown) vs WAR on standalone Tomcat
  (`ServletInitializer`).
- Actuator: health/liveness/readiness, metrics, caches.

**Read the code**
- `SkyRouteProperties`, `application.yml`, `FlightController`, `BookingController`.
- `ServletInitializer` (WAR deployment) and `server.shutdown: graceful`.

**Lab**
- Add `GET /api/v1/flights/{id}/seatmap` returning a mock seat map. Validate the
  path variable and document it in Bruno.
- Package as a WAR and deploy to a standalone Tomcat 10.1 (Manual §6.4).

**Checkpoint:** explain what happens between "HTTP request hits port 8080" and
"controller method runs".

---

## Module 6 — Filters, interceptors and cross-cutting concerns (½ week)

**Learn**
- Servlet `Filter` vs Spring `HandlerInterceptor` vs AOP; ordering (`@Order`);
  `OncePerRequestFilter`.
- Where Spring Security's filter chain sits; `addFilterBefore`.
- gRPC `ServerInterceptor` as the gRPC equivalent.

**Read the code**
- `CorrelationIdFilter`, `RequestLoggingFilter`, `JwtAuthenticationFilter`, `JwtServerInterceptor`.

**Lab**
- Write a rate-limiting filter (Bucket4j or a simple token bucket) for
  `/api/v1/flights/search`, keyed by IP. It returns 429 with a Problem Detail.

**Checkpoint:** why `JwtAuthenticationFilter` isn't a `@Component`.

---

## Module 7 — Spring Security and JWT (1 week)

**Learn**
- Authentication vs authorization; `SecurityFilterChain`; stateless sessions;
  why CSRF doesn't apply to header-token APIs.
- Password hashing (BCrypt), `UserDetailsService`, `AuthenticationManager`.
- JWT anatomy (header.payload.signature), HS256 vs RS256, claims (`sub`, `exp`,
  `iss`, custom roles), clock skew, key rotation, refresh tokens. Why you don't put
  secrets in a JWT.
- URL-based rules vs method security (`@PreAuthorize`). Ownership checks.
- CORS. 401 vs 403 with JSON bodies.
- Spring Security as an OAuth2 Resource Server (production alternative).

**Read the code**
- `auth/*`: `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `MongoUserDetailsService`, `AuthController`.
- `BookingService.loadOwned`: returns 404 rather than 403 for other agents' PNRs.
- Tests: `JwtServiceTest` (expiry, forged signature), `FlightControllerTest` (401/403).

**Lab**
- Add refresh tokens: a `refreshTokens` collection with a TTL index, and `POST /auth/refresh`.
- Replace HS256 with RS256 using a key pair; explain why other services now only
  need the public key.

**Checkpoint:** draw the full request flow for `POST /api/v1/bookings` with a valid token.

---

## Module 8 — MongoDB from zero to expert (2 weeks, the deepest module)

Follow **`04-mongodb-mastery.md`** in order. It has its own 8 levels and exercises.

**Week 1 — fundamentals to intermediate**
- Documents, BSON types (ObjectId, Date, Decimal128, int32/int64), collections, databases.
- CRUD in `mongosh`; query operators (`$eq/$gt/$in/$or/$exists/$regex/$elemMatch`);
  projections; sort/skip/limit; cursors.
- Update operators: `$set/$unset/$inc/$mul/$min/$max/$currentDate`, arrays
  (`$push/$addToSet/$pull/$each/$slice`), positional `$`, `$[]`, `$[id]` + `arrayFilters`,
  upserts, `findOneAndUpdate`.
- Data modelling: embed vs reference, the 16 MB limit, patterns (extended
  reference, subset, computed, bucket, outlier, schema versioning).

**Week 2 — advanced to expert**
- Indexes: single, compound (ESR rule), multikey, partial, TTL, unique, text,
  wildcard; covered queries; `explain("executionStats")`.
- Aggregation pipeline: `$match/$group/$project/$unwind/$lookup/$facet/$bucket/$setWindowFields`.
- Atomicity, write/read concerns, transactions (replica set), retryable writes,
  optimistic locking (`@Version`).
- Schema validation (`$jsonSchema`), change streams, replication and failover,
  sharding (shard key choice), backup and restore, security (auth, TLS, roles).
- Spring Data MongoDB: `MongoRepository` derived queries, `@Query`, `MongoTemplate`,
  `Criteria`, `Update`, `Aggregation`, auditing, index annotations, converters.

**Read the code**
- `Flight` + `CabinInventory` (embedding, Decimal128, compound indexes).
- `FlightSearchQueryBuilder` (dynamic `Criteria`, `$elemMatch`, paging, projection).
- `FlightInventoryRepository` (atomic conditional `$inc`, positional `$`, `arrayFilters`).
- `ReportService` (two aggregation pipelines), `BookingFlowIT` (concurrency proof).
- `mongo/01-indexes.js`, `02-schema-validation.js`, `03-exercises.js`.

**Labs**
- Do all 17 exercises in `mongo/03-exercises.js` without looking at the answers.
- Add a `GET /api/v1/reports/daily-bookings` endpoint using `$dateTrunc` + `$group`.
- Turn the booking flow into a multi-document transaction (Mastery §6.4) and compare
  it with the compensation approach.
- Run `explain()` on the search query with and without the `route_departure` index.

**Checkpoint:** design the collections for "seat map with individual seat
assignment" and defend embed vs reference, indexes and the atomic update you'd use.

---

## Module 9 — Caching (½ week)

**Learn**
- Spring Cache abstraction (`@Cacheable/@CacheEvict/@CachePut`), cache keys
  (records!), the proxy self-invocation pitfall.
- Caffeine (local) vs Redis (distributed); TTL, size bounds, stats; cache-aside
  vs write-through; stampede protection.
- What *not* to cache (the write path, authorization decisions, per-user data
  with shared keys).

**Read the code**
- `CacheConfig` (two caches, different TTLs), `AirportService.get`,
  `FlightService.search`, and `@CacheEvict` on writes.

**Lab**
- Expose `/actuator/caches` and the Caffeine hit ratio via `/actuator/metrics/cache.gets`.
- Swap Caffeine for Redis using `spring-boot-starter-data-redis`. No business code should change.

**Checkpoint:** a customer sees 3 seats in search but booking fails with
SEATS_UNAVAILABLE. Is that a bug? Explain.

---

## Module 10 — gRPC and Protocol Buffers (1 week)

**Learn**
- Why gRPC: HTTP/2, binary protobuf, contracts, streaming, generated clients.
- Proto3: messages, enums (zero value), `repeated`, `oneof`, well-known types,
  field numbers, `reserved`, backward and forward compatibility rules.
- RPC types: unary, server streaming, client streaming, bidirectional.
- Channels (long-lived, pooled connections) vs stubs (blocking/async/future);
  deadlines; metadata; status codes; interceptors; in-process testing.
- REST vs gRPC: when each fits (browser channels vs service-to-service).

**Read the code**
- `src/main/proto/flight_availability.proto`, the `protobuf-maven-plugin` in `pom.xml`.
- `FlightAvailabilityGrpcService`, `JwtServerInterceptor`, `GrpcServerLifecycle`, `FlightAvailabilityClient`.
- `FlightAvailabilityGrpcServiceTest` (in-process server + channel + stubs).
- `api-clients/grpc-requests.md` (grpcurl / Postman).

**Lab**
- Add `rpc HoldSeats(HoldRequest) returns (HoldResponse)` and map business
  errors to `FAILED_PRECONDITION`/`RESOURCE_EXHAUSTED`.
- Add a field to `FlightOption`, regenerate, and prove an old client still works.
- Enable server reflection so grpcurl works without `-proto`.

**Checkpoint:** explain what breaks if you change `int32 seats_available = 7`
to `int64 seats_available = 7`, and what if you renumber it.

---

## Module 11 — Testing strategy: JUnit 5, Spock, Testcontainers, Playwright (1 week)

**Learn**
- The test pyramid; what each level proves.
- JUnit 5: lifecycle, `@ParameterizedTest`, `@RepeatedTest`, assertions with AssertJ, extensions.
- Mockito: mocks vs stubs vs spies; verifying interactions (compensation!).
- Spring test slices: `@WebMvcTest` (+ security), `@DataMongoTest`, `@SpringBootTest`.
- Testcontainers: real MongoDB in Docker, `@DynamicPropertySource`.
- **Spock (Groovy):** `given/when/then`, `where:` data tables, `thrown()`, readable specs.
- **Playwright:** locators by role/label, auto-waiting, `page.route()` network
  stubbing, traces, CI reporters.
- Coverage with JaCoCo; mutation testing (PIT) as a stretch goal.

**Read the code**
- `BookingServiceTest` (compensation verified with Mockito), `FlightSearchQueryBuilderTest`,
  `FlightControllerTest`, `JwtServiceTest`, `FarePricingSpec.groovy`, `BookingFlowIT`,
  `frontend/e2e/booking.spec.ts`.

**Lab**
- Write a Spock spec for `BookingService.validateItinerary` with a `where:` table of 6 itineraries.
- Add a Playwright test for cancelling a booking from "My bookings".
- Run `mvn verify` with Docker running and watch `BookingFlowIT` prove "no oversell" with 50 threads.

**Checkpoint:** for each test file, say what bug it would catch that the others wouldn't.

---

## Module 12 — CI/CD: Jenkins, Groovy pipelines, SonarQube (1 week)

**Learn**
- Jenkins: controllers vs agents, declarative vs scripted pipelines, stages,
  `post`, credentials binding, `input` approvals, multibranch pipelines, webhooks.
- Groovy essentials for Jenkins: closures, maps, string interpolation (and its
  injection risk), shared libraries (`vars/`, `src/`).
- SonarQube: rules, quality profiles, quality gates, "clean as you code" (new-code
  conditions), Sonar scanner for Maven, JaCoCo XML, PR decoration, SonarLint in IntelliJ.
- **Updating Sonar rules:** UI vs Web API, copying "Sonar way", activating,
  deactivating and re-parameterising rules, severity changes, versioning
  profiles as code, and handling false positives (`//NOSONAR` vs "Won't fix" vs rule change).
- Docker multi-stage builds; promotion between environments; rollback.

**Read the code**
- `Jenkinsfile`, `ci/jenkins/shared-library/vars/mavenServicePipeline.groovy`,
  `ci/sonar/update-quality-profile.sh`, `backend/Dockerfile`, `pom.xml` (jacoco + sonar properties).

**Lab**
- Run Jenkins and SonarQube locally in Docker (Manual §8.5). Create a
  multibranch job for this repo.
- Use `update-quality-profile.sh` to lower cognitive complexity to 10, find the
  method that now fails, and refactor it.
- Add a Sonar stage to the shared library that takes a `qualityGate` parameter.

**Checkpoint:** a teammate wants to disable a rule because "it's noisy". How
do you decide, and how do you make the change?

---

## Module 13 — React front end and full-stack debugging (½ week)

**Learn**
- React with hooks, React Router, a typed API client, handling Problem Details
  in the UI, auth token storage trade-offs.
- Vite dev proxy vs CORS. Browser DevTools: network tab plus correlation ID.

**Read the code**
- `frontend/src/api/client.ts`, `pages/*`, `vite.config.ts`, `playwright.config.ts`.

**Lab**
- Add a cabin selector and passenger count to booking; show field errors from `fieldErrors`.

---

## Module 14 — System design capstone (1 week)

Pick one, write an ADR (`docs/adr/` template in the manual) and implement a slice:

1. **Seat holds with expiry:** hold seats for 15 minutes during payment (TTL
   index + reconciliation job), idempotent "confirm" endpoint.
2. **Schedule change and re-accommodation:** when a flight is cancelled, find
   affected PNRs (`segments.flightId` index) and propose alternatives; publish
   events via MongoDB change streams.
3. **Fare search at scale:** pre-computed availability per route and day
   (computed pattern), Redis cache, gRPC streaming to a partner channel.

**Deliverable:** design doc (context, requirements, options, decision,
trade-offs, failure modes, metrics), code, tests, and a 15-minute walkthrough
as if you were interviewing.

---

## Progress tracker

| # | Module | Lab done | Checkpoint passed |
|---|---|---|---|
| 0 | Setup & domain | ☐ | ☐ |
| 1 | Core Java & Java 17 | ☐ | ☐ |
| 2 | Streams & lambdas | ☐ | ☐ |
| 3 | SOLID | ☐ | ☐ |
| 4 | Exceptions & logging | ☐ | ☐ |
| 5 | Spring Boot & REST | ☐ | ☐ |
| 6 | Filters | ☐ | ☐ |
| 7 | Security & JWT | ☐ | ☐ |
| 8 | MongoDB | ☐ | ☐ |
| 9 | Caching | ☐ | ☐ |
| 10 | gRPC & protobuf | ☐ | ☐ |
| 11 | Testing | ☐ | ☐ |
| 12 | Jenkins & Sonar | ☐ | ☐ |
| 13 | React | ☐ | ☐ |
| 14 | Capstone | ☐ | ☐ |
