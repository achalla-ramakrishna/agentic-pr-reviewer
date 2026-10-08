# SkyRoute Engineering Manual

How the reference application is built, and why, using the stack a Sabre-style
airline platform team uses. Read it alongside the code: each section names the
files that show the topic.

**Contents**
1. [Airline domain primer](#1-airline-domain-primer)
2. [Architecture](#2-architecture)
3. [Run it locally](#3-run-it-locally)
4. [Code map and design principles](#4-code-map-and-design-principles)
5. [Java 17, streams and lambdas in practice](#5-java-17-streams-and-lambdas-in-practice)
6. [Spring Boot, REST and Tomcat](#6-spring-boot-rest-and-tomcat)
7. [Security: Spring Security and JWT](#7-security-spring-security-and-jwt)
8. [CI/CD: Jenkins, Groovy and SonarQube](#8-cicd-jenkins-groovy-and-sonarqube)
9. [gRPC, Protocol Buffers, channels and stubs](#9-grpc-protocol-buffers-channels-and-stubs)
10. [Testing strategy](#10-testing-strategy)
11. [Caching](#11-caching)
12. [Errors, logging and observability](#12-errors-logging-and-observability)
13. [Working style: asking the right questions, ADRs](#13-working-style-asking-the-right-questions-adrs)
14. [Production readiness checklist](#14-production-readiness-checklist)
15. [How the job-spec keywords map to this repo](#15-how-the-job-spec-keywords-map-to-this-repo)

---

## 1. Airline domain primer

| Term | Meaning | In SkyRoute |
|---|---|---|
| **PSS** | Passenger Service System: an airline's core reservations, inventory and departure-control platform | The whole app is a mini PSS |
| **GDS** | Global Distribution System that travel agencies use to shop and book many airlines | Agents (`AGENT` role) are the "channel" |
| **Channel** | A sales/distribution route: airline web/app, call centre, OTA, GDS, NDC API | REST serves web; gRPC serves machine channels |
| **NDC** | IATA's New Distribution Capability, an XML/JSON API standard for airline offers and orders | Out of scope; the gRPC API plays the "partner API" role |
| **Flight / leg** | One take-off and landing on a specific date (e.g. SR101 DFW→JFK on 1 Nov) | `Flight` document |
| **Segment** | A flight as booked inside a reservation | `BookedSegment` (embedded in `Booking`) |
| **Itinerary** | Ordered segments, possibly with connections | `CreateBookingRequest.segments` |
| **MCT** | Minimum Connection Time between arriving and departing flights at an airport | `BookingService.MIN_CONNECTION_TIME` (45 min) |
| **Cabin** | Physical compartment: Economy, Premium Economy, Business, First | `CabinClass` |
| **Booking class (RBD)** | Fare bucket letter (Y, B, M…) inside a cabin, used for revenue management | Simplified away: one fare per cabin |
| **Inventory / availability** | Seats left to sell per flight and cabin/class | `CabinInventory.seatsAvailable` |
| **PNR / record locator** | The reservation record, identified by a 6-character code | `Booking.recordLocator` |
| **Passenger types** | ADT (adult), CHD (child), INF (lap infant: no seat) | `PassengerType` |
| **Fare** | Price for a passenger on a segment, before taxes | `FarePricingService` |
| **Ticket / EMD** | Payment document for travel / ancillaries | Not modelled; a natural extension |
| **Load factor** | Seats sold ÷ seats offered | `ReportService.loadFactor` |
| **Schedule change / IRROPS** | Airline-initiated changes and irregular operations (delays, cancellations) that force rebooking | `PATCH /flights/{id}/status`; capstone 2 |

**The single most important invariant:** never sell a seat that doesn't exist.
Everything in §2.3 serves this invariant.

---

## 2. Architecture

### 2.1 Components

```
  ┌───────────────┐   REST/JSON + JWT    ┌──────────────────────────────────────────────┐
  │ React (Vite)  │ ───────────────────▶ │  skyroute-api (Spring Boot 3, Java 17)       │
  │  frontend/    │                      │                                              │
  └───────────────┘                      │  Tomcat :8080          Netty gRPC :9090      │
                                         │  ┌──────────────────┐  ┌──────────────────┐  │
  ┌───────────────┐   gRPC/protobuf+JWT  │  │ Servlet filters  │  │ ServerInterceptor │  │
  │ Partner/OTA   │ ───────────────────▶ │  │ CorrelationId    │  │ JwtServer...      │  │
  │ channel       │                      │  │ RequestLogging   │  └────────┬─────────┘  │
  └───────────────┘                      │  │ Security chain   │           │            │
                                         │  │  └ JwtAuthFilter │           │            │
  ┌───────────────┐                      │  └────────┬─────────┘           │            │
  │ Bruno/Postman │ ──── REST ─────────▶ │   Controllers (auth, airport, flight,        │
  │ grpcurl       │ ──── gRPC ─────────▶ │   booking, report)   gRPC service adapter    │
  └───────────────┘                      │           └──────────┬──────────┘            │
                                         │   Services: FlightService, BookingService,   │
                                         │   FarePricingService, ReportService          │
                                         │   (+ Caffeine caches via Spring Cache)       │
                                         │           │                                  │
                                         │   Spring Data MongoDB: repositories,         │
                                         │   MongoTemplate (Criteria, Update, Aggreg.)  │
                                         └───────────┼──────────────────────────────────┘
                                                     ▼
                                     MongoDB 7 (replica set rs0) — users, airports,
                                     flights (embedded cabins), bookings (embedded pax/segments)
```

### 2.2 Packages (package-by-feature)

```
com.skyroute
├── auth       AppUser, Role, JwtService, JwtAuthenticationFilter, SecurityConfig, AuthController
├── airport    Airport (natural key _id = IATA), repository with @Query regex, cached service
├── flight     Flight + CabinInventory, search criteria/builder, inventory (atomic updates), service, controller
├── booking    Booking + Passenger + BookedSegment, fare rules (strategy), PNR generator, service, controller
├── report     Aggregation pipelines (route revenue, load factor)
├── grpc       Generated-stub adapter, JWT interceptor, server lifecycle, example client
├── seed       Dev data loader
└── common     error (exception hierarchy + handler), logging (filters), config (cache, mongo, properties)
```

Package-by-feature keeps everything about "booking" together. Deleting or
extracting a feature into its own microservice later means moving one folder.

### 2.3 The booking flow (the critical path)

```
POST /api/v1/bookings
 1. CorrelationIdFilter     → MDC correlationId, response header
 2. JwtAuthenticationFilter → SecurityContext(agent, ROLE_AGENT)
 3. BookingController       → @Valid request, Caller(username, admin)
 4. BookingService.create
    a. validatePassengerMix   (≥1 adult, infants ≤ adults)
    b. load flights, validateItinerary (sellable, connected, MCT ≥ 45 min)
    c. for each segment: FlightInventoryRepository.holdSeats   ← ATOMIC conditional $inc
          fails? → SeatsUnavailableException
    d. price (FarePricingService), build Booking, insert with unique PNR (retry on E11000)
    e. any exception after (c) started → release held seats in reverse order (compensation), rethrow
 5. @CacheEvict flightSearch (seat counts changed)
 6. 201 Created + Location: /api/v1/bookings/{PNR}
```

Why step 4c can't oversell: the filter and the decrement run in a **single
document update**, which MongoDB applies atomically:

```js
db.flights.updateOne(
  { _id: id, status: { $in: ["SCHEDULED","DELAYED"] },
    cabins: { $elemMatch: { cabinClass: "ECONOMY", seatsAvailable: { $gte: 2 } } } },
  { $inc: { "cabins.$.seatsAvailable": -2 } })
// modifiedCount == 1 → held; 0 → not enough seats
```

Two agents racing for the last seat both send this. Only one can match
`seatsAvailable >= 1`; the second matches nothing. `BookingFlowIT.neverOversellsUnderConcurrentLoad`
proves it with 50 threads and 10 seats.

**Why compensation instead of a transaction?** Multi-document transactions need
a replica set and add latency and contention. The saga/compensation style is
how many large booking systems work. The trade-off: if the process crashes
between hold and insert, seats leak, which is safe (we undersell) but needs a
reconciliation job. See `04-mongodb-mastery.md` §6.4 for the transactional version.

### 2.4 Data model

```js
// flights
{ _id: ObjectId, flightNumber: "SR101", carrierCode: "SR", origin: "DFW", destination: "JFK",
  departureTime: ISODate, arrivalTime: ISODate, departureDate: "2026-11-01",
  aircraftType: "B738", status: "SCHEDULED",
  cabins: [ { cabinClass: "ECONOMY", capacity: 150, seatsAvailable: 148,
              baseFare: NumberDecimal("189"), currency: "USD" }, ... ],
  version: 3, createdAt: ISODate, updatedAt: ISODate, _class: "com.skyroute.flight.Flight" }

// bookings
{ _id: ObjectId, recordLocator: "K7QXPM", status: "CONFIRMED", contactEmail: "...",
  passengers: [ { firstName, lastName, type: "ADT", dateOfBirth } ],
  segments: [ { flightId: "6720…", flightNumber: "SR101", origin, destination, departureTime,
                cabinClass: "ECONOMY", amount: NumberDecimal("189.00"), currency: "USD" } ],
  passengerCount: 1, totalAmount: NumberDecimal("189.00"), currency: "USD",
  createdBy: "agent", version: 0, createdAt, updatedAt }
```

Key decisions (each one is an interview question):

- **Cabins embedded in flights:** bounded (≤ 4), always read and updated together, and atomic updates are only guaranteed within one document.
- **Segments copy flight data** (extended reference pattern): the PNR shows what was sold, and reading it needs no join.
- **Money as Decimal128:** Spring Data would store `BigDecimal` as a string by default (`@Field(targetType = DECIMAL128)`).
- **`departureDate` as an ISO string:** `LocalDate` would be stored as a timestamp at midnight in the JVM's time zone.
- **`@Version` optimistic locking** on documents that are read-modified-saved (`Booking.cancel`).

---

## 3. Run it locally

**Prerequisites:** JDK 17+, Maven 3.9+, Docker, Node 20+, optionally `mongosh`, Bruno/Postman, grpcurl.

```bash
cd skyroute-academy

# 1. Infrastructure (MongoDB replica set + mongo-express UI on :8081)
docker compose up -d mongo mongo-express

# 2. Backend (seeds 2 users, 7 airports, 240 flights on first start)
cd backend
mvn spring-boot:run                     # REST :8080, gRPC :9090
# or in IntelliJ: run SkyRouteApplication (set env MONGODB_URI if Mongo isn't on localhost)

# 3. Frontend
cd ../frontend
npm install && npm run dev              # http://localhost:5173 (proxies /api → :8080)

# 4. Optional: explicit indexes + schema validation
mongosh "mongodb://localhost:27017/skyroute" ../mongo/01-indexes.js
mongosh "mongodb://localhost:27017/skyroute" ../mongo/02-schema-validation.js
```

Logins: `agent / agent123` (AGENT), `admin / admin123` (ADMIN + AGENT).

| Command (from `backend/`) | What it runs |
|---|---|
| `mvn test` | JUnit 5 + Mockito + Spring slices + Spock + gRPC in-process tests |
| `mvn verify` | The above plus Testcontainers ITs (needs Docker) plus JaCoCo report |
| `mvn sonar:sonar -Dsonar.host.url=… -Dsonar.token=…` | SonarQube analysis |
| `docker compose up --build` (repo root) | Everything in containers |

| Command (from `frontend/`) | What it runs |
|---|---|
| `npm run build` | Type-check + production build |
| `npm run test:e2e` | Playwright (builds, serves on :4173, stubs the API) |

**IntelliJ tips:** enable *Settings → Build → Compiler → Annotation Processors*;
mark `target/generated-sources/protobuf/{java,grpc-java}` as *Generated
Sources Root* (Maven reload usually does this); install the **SonarLint** plugin
and bind it to your SonarQube server so you see the same rules as CI; use the
*Services* tool window to run the backend and frontend together.

---

## 4. Code map and design principles

### 4.1 SOLID map

| Principle | Where | What to notice |
|---|---|---|
| **S**ingle responsibility | `FlightSearchQueryBuilder` vs `FlightService`; `CorrelationIdFilter` vs `RequestLoggingFilter` | Each class has one reason to change; the builder is testable without a DB |
| **O**pen/closed | `PassengerFareRule` + `FareRules` | A new passenger type is a new bean; `FarePricingService` is untouched |
| **L**iskov | `SkyRouteException` subclasses | Any subclass can be handled by `handleDomain` with no `instanceof` checks |
| **I**nterface segregation | `FlightRepository` (simple CRUD) vs `FlightInventoryRepository` (atomic ops) | Callers depend only on the operations they need |
| **D**ependency inversion | `FarePricingService(List<PassengerFareRule>)`, `JwtService(…, Clock)` | Depends on abstractions; `Clock` injection makes time testable |

### 4.2 Other patterns used

- **Adapter:** `FlightAvailabilityGrpcService` translates protobuf ↔ domain, `MongoUserDetailsService` translates `AppUser` → `UserDetails`.
- **Strategy + registry:** fare rules collected into an `EnumMap`.
- **Rich domain model:** `Booking.cancel()` owns its invariants.
- **Compensating transaction (saga step):** `BookingService.create`.
- **Value objects:** records everywhere for DTOs and embedded values.

### 4.3 Conventions

- Constructor injection only. No field `@Autowired`, no Lombok (records cover most of it).
- Documents (`Flight`, `Booking`) are classes with private setters. DTOs and embedded values are records.
- Every public behaviour has a test in the mirrored test package.
- No `double` for money, no `new Date()` (use injected `Clock`), no string concatenation in log calls.

---

## 5. Java 17, streams and lambdas in practice

| Feature | Example in repo |
|---|---|
| Records + compact constructor | `FlightSearchCriteria` normalises and defaults input once |
| Nested records as namespaces | `FlightDtos`, `BookingDtos`, `SkyRouteProperties.Jwt` |
| Switch expressions (exhaustive) | `FlightService.comparatorFor`, `FlightAvailabilityGrpcService.toCabin` |
| Sealed types + pattern matching | `StreamsAndLambdasKataTest.ex7` |
| `Stream.toList()` | Throughout services |
| `Optional.stream()` + `flatMap` | `FlightService.search` |
| `groupingBy` + `counting` | `BookingService.validatePassengerMix` |
| `toMap` with merge + `EnumMap` supplier | `FarePricingService` |
| `reduce(BigDecimal.ZERO, BigDecimal::add)` | `FarePricingService.priceSegment`, `BookingService.buildBooking` |
| `RandomGenerator` (Java 17) | `RecordLocatorGenerator` |
| Text blocks | `StreamsAndLambdasKataTest.ex8` |

> Java 17 gotcha found while building this repo: `ExecutorService` is **not**
> `AutoCloseable` until Java 19, so try-with-resources on it fails to compile
> with `--release 17`. See `BookingFlowIT`.

**Rules of thumb for streams in review:**
- No side effects inside `map/filter`. Use `forEach` only at the end, and only for I/O.
- Prefer a method reference when it reads better (`Leg::flight`), and a lambda when it doesn't.
- If a stream needs comments to explain it, a loop or a well-named helper method is usually clearer.
- Never use parallel streams in a request thread without measuring first.

---

## 6. Spring Boot, REST and Tomcat

### 6.1 Configuration
`application.yml` → `SkyRouteProperties` (a validated record). Every secret
comes from env vars (`SKYROUTE_JWT_SECRET`, `MONGODB_URI`). The dev defaults are
clearly labelled and must be overridden in any real environment.

### 6.2 REST API

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/login` | public | → JWT |
| GET | `/api/v1/airports?q=dal` | public | regex prefix (quoted), cached by code |
| GET | `/api/v1/airports/{code}` | public | cached 12h |
| GET | `/api/v1/flights/search?origin&destination&date&cabin&passengers&maxFare&sort&page&size` | public | cached 30s |
| GET | `/api/v1/flights/{id}` | public | |
| POST | `/api/v1/flights` | ADMIN | 201 + Location |
| PATCH | `/api/v1/flights/{id}/status` | ADMIN | `$set`, evicts search cache |
| PATCH | `/api/v1/flights/{id}/cabins/{cabin}/fare` | ADMIN | positional `$set` |
| POST | `/api/v1/bookings` | AGENT/ADMIN | 201 + Location |
| GET | `/api/v1/bookings` | AGENT/ADMIN | own bookings, paged |
| GET | `/api/v1/bookings/{pnr}` | owner or ADMIN | 404 for others |
| POST | `/api/v1/bookings/{pnr}/cancel` | owner or ADMIN | state change, not DELETE |
| GET | `/api/v1/reports/route-revenue?from&to` | ADMIN | aggregation |
| GET | `/api/v1/reports/load-factor?date` | ADMIN | aggregation |
| GET | `/actuator/health` | public | liveness/readiness probes |

Errors are always `application/problem+json` (see §12).

### 6.3 Embedded Tomcat
`spring-boot-starter-web` brings embedded Tomcat. Tuning lives in `server.tomcat.*`
(threads, connection timeouts). `server.shutdown: graceful` lets in-flight
requests finish on SIGTERM, which matters for Kubernetes rolling deploys.

### 6.4 Deploying as a WAR to standalone Tomcat
Some enterprise estates run shared Tomcat servers:
1. In `pom.xml` set `<packaging>war</packaging>` and mark
   `spring-boot-starter-tomcat` as `<scope>provided</scope>`.
2. `ServletInitializer` (already present) bootstraps Spring inside the container.
3. `mvn package` → copy `target/skyroute-api-*.war` to `$CATALINA_HOME/webapps/`.
   Use **Tomcat 10.1+** (Jakarta EE 10 namespace, required by Spring Boot 3).
4. The gRPC server still starts on 9090 inside the webapp, so make sure the port is free.

---

## 7. Security: Spring Security and JWT

```
Login:    POST /auth/login {u,p} → AuthenticationManager → DaoAuthenticationProvider
          → MongoUserDetailsService → BCrypt match → JwtService.issue → {accessToken}

Request:  Authorization: Bearer eyJ…
          → JwtAuthenticationFilter → JwtService.verify (signature, iss, exp)
          → SecurityContext(username, ROLE_x)
          → authorizeHttpRequests rules (URL) → controller → service ownership check
```

- **Stateless:** no HTTP session, so the app scales horizontally without sticky sessions.
- **CSRF disabled** because the browser never sends credentials automatically
  (no cookies). If you move the token into a cookie, turn CSRF back on.
- **401 vs 403** are produced as Problem Details by the entry point and access-denied handler in `SecurityConfig`.
- **Ownership:** `BookingService.loadOwned` returns **404** for another agent's
  PNR, so a stranger can't confirm that it exists.
- **Production upgrades:** RS256/ES256 with keys from a vault or KMS, key rotation via
  `kid`, short access tokens plus refresh tokens, or delegate to an identity provider
  and use `spring-boot-starter-oauth2-resource-server`.

---

## 8. CI/CD: Jenkins, Groovy and SonarQube

### 8.1 Pipeline (`Jenkinsfile`)
`Checkout → Backend build & tests (verify) → Sonar analysis → Quality gate
(blocks) → Frontend build + Playwright → Docker image (main) → Deploy dev →
manual approval → Deploy prod`.

Notes:
- JUnit, Failsafe and Playwright JUnit reports are published so Jenkins shows trends.
- `waitForQualityGate abortPipeline: true` needs a SonarQube webhook to
  `https://<jenkins>/sonarqube-webhook/`.
- The image tag is the git SHA: immutable, so a rollback means redeploying the previous tag.

### 8.2 Groovy you need for Jenkins
- Closures (`steps { … }` is a closure), maps (`config.get('runE2e', false)`), GStrings.
- **Single vs double quotes in `sh`:** `"${PASSWORD}"` is interpolated by Groovy
  and leaks into logs and the process list. Use single-quoted `sh '… $PASSWORD'` so the
  *shell* expands it (see the Docker stage).
- Shared libraries: `vars/*.groovy` become global steps.
  `ci/jenkins/shared-library/vars/mavenServicePipeline.groovy` is a template for
  many services.

### 8.3 SonarQube concepts
- **Rule:** one check (e.g. `java:S2142`, "InterruptedException should not be ignored").
- **Quality profile:** the set of active rules and their parameters for a language.
- **Quality gate:** pass/fail conditions on metrics, ideally on **new code**
  (coverage ≥ 80%, no new bugs or vulnerabilities, duplication ≤ 3%).

### 8.4 Updating Sonar rules (how to do it properly)
1. **Never edit "Sonar way" directly.** Copy it (it's read-only anyway) so
   upgrades don't silently change your rules.
2. Make changes **as code**: `ci/sonar/update-quality-profile.sh` uses the Web
   API (`api/qualityprofiles/activate_rule`, `deactivate_rule`, `set_default`,
   `api/qualitygates/create_condition`). Review it in a PR like any other change.
3. **Parameterise rather than disable.** For example, cognitive complexity
   threshold 15 → 12 (`java:S3776`, `Threshold=12`).
4. For a **false positive**, mark the issue "False positive" in the UI with a
   comment. `// NOSONAR` hides everything on the line, so treat it as a last resort.
5. Disabling a rule needs a written reason (the script has comments for each change).
6. Developers run **SonarLint in IntelliJ in connected mode**, so they see the exact profile before they push.
7. Custom rules: write a plugin with `sonar-java`'s `IssuableSubscriptionVisitor`
   API when a team convention can't be expressed by parameters (e.g. "no
   `MongoTemplate` outside `*Repository` classes"). Most teams never need this.

### 8.5 Try it locally
```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
# login admin/admin → create token → then:
cd backend && mvn verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>

docker run -d --name jenkins -p 8090:8080 -v jenkins_home:/var/jenkins_home jenkins/jenkins:lts-jdk17
# install plugins: Pipeline, Git, JUnit, JaCoCo, SonarQube Scanner, Docker Pipeline
```

---

## 9. gRPC, Protocol Buffers, channels and stubs

- **Contract first:** `backend/src/main/proto/flight_availability.proto`.
  `protobuf-maven-plugin` runs `protoc` + `protoc-gen-grpc-java` during
  `mvn compile` → `target/generated-sources/protobuf/`.
- **Server:** `FlightAvailabilityGrpcService` extends the generated `ImplBase`.
  `GrpcServerLifecycle` (a `SmartLifecycle`) starts and stops a Netty server with Spring.
- **Interceptors:** `JwtServerInterceptor` checks `authorization` metadata,
  rejects with `UNAUTHENTICATED`, and puts the username into the gRPC `Context`.
- **Channel:** a long-lived, thread-safe, multiplexed HTTP/2 connection. Create
  one per target service and reuse it. **Stub:** a lightweight, typed client
  bound to a channel. Create stubs freely and set **deadlines** per call
  (`withDeadlineAfter`). See `FlightAvailabilityClient`.
- **Testing:** `InProcessServerBuilder` and `InProcessChannelBuilder` give a real
  gRPC stack without sockets (`FlightAvailabilityGrpcServiceTest`).
- **Schema evolution rules:** never change a field's number or type, never reuse
  a deleted number (mark it `reserved`), add new fields with new numbers, and make
  every enum's zero value `*_UNSPECIFIED`.
- **Error mapping:** validation → `INVALID_ARGUMENT`, auth → `UNAUTHENTICATED`,
  unexpected → `INTERNAL` (logged, details hidden). Add `NOT_FOUND`,
  `FAILED_PRECONDITION` and `RESOURCE_EXHAUSTED` as the API grows.

---

## 10. Testing strategy

| Layer | Tool | Example | Proves |
|---|---|---|---|
| Pure unit | JUnit 5 + AssertJ | `FarePricingServiceTest`, `RecordLocatorGeneratorTest` | Logic, rounding |
| Unit with collaborators | Mockito | `BookingServiceTest` | Compensation, retries, auth rules |
| Query shape | JUnit (no DB) | `FlightSearchQueryBuilderTest`, `ReportServiceTest` | The exact Mongo filter / pipeline sent |
| Specification | **Spock** (Groovy) | `FarePricingSpec.groovy` | Business rules as readable tables |
| Web slice | `@WebMvcTest` + Spring Security Test | `FlightControllerTest` | Binding, validation, 401/403, Problem JSON, filters |
| gRPC | in-process server/channel | `FlightAvailabilityGrpcServiceTest` | Proto mapping, interceptor, status codes |
| Integration | Testcontainers MongoDB | `BookingFlowIT` | Real atomicity, aggregation, Decimal128 |
| Learning | JUnit | `StreamsAndLambdasKataTest` | Your Java 17 and streams skills |
| End-to-end UI | **Playwright** | `frontend/e2e/booking.spec.ts` | User journeys, auth header, error display |

**Why both query-shape tests and Testcontainers?** Shape tests run in
milliseconds on every save. The IT proves that MongoDB actually behaves the way
we think (e.g. that `$elemMatch` + positional `$` decrements the right cabin).

---

## 11. Caching

| Cache | Key | TTL | Evicted by |
|---|---|---|---|
| `airports` | IATA code | 12h | (reference data) |
| `flightSearch` | `FlightSearchCriteria` record | 30s | flight create/status/fare changes, booking create/cancel |

- Records make good cache keys (value-based `equals`/`hashCode`).
- **Self-invocation pitfall:** `@Cacheable` works through a proxy, so calling a cached
  method from the same class bypasses the cache.
- **Staleness is a product decision.** Search may show 3 seats that were just
  sold. The write path (atomic hold) is the source of truth and returns a clean
  409 `SEATS_UNAVAILABLE`.
- Multi-instance: Caffeine caches are per JVM, so evictions don't propagate. Use
  Redis (`spring-boot-starter-data-redis`) for shared caches. The annotations stay the same.

---

## 12. Errors, logging and observability

**Error contract** (`GlobalExceptionHandler`):
```json
{
  "type": "https://skyroute.example/errors/seats-unavailable",
  "title": "Conflict", "status": 409,
  "detail": "Flight SR101 has fewer than 2 seat(s) left in ECONOMY",
  "errorCode": "SEATS_UNAVAILABLE",
  "correlationId": "4f0c…", "timestamp": "2026-10-08T09:22:46Z"
}
```

| Exception | Status | Logged at |
|---|---|---|
| `NotFoundException` | 404 | WARN, no stack |
| `SeatsUnavailableException`, `DuplicateKey`, `OptimisticLocking` | 409 | WARN/INFO |
| `BusinessRuleException` | 422 | WARN |
| Validation | 400 + `fieldErrors` | none |
| Anything else | 500, generic message | ERROR with stack |

**Logging rules**
- `private static final Logger log = LoggerFactory.getLogger(X.class);`, the SLF4J API only.
- Parameterised: `log.info("Booking {} created by {}", pnr, user)`.
- Levels: ERROR = someone must act; WARN = unexpected but handled; INFO =
  business events (booking created/cancelled, flight status changes); DEBUG = diagnostics.
- Never log passwords, tokens, full card numbers, or passport numbers. Treat email
  and names as PII; mask them in production.
- Every line has `cid=<correlationId>` (`logback-spring.xml`). The `prod` profile
  adds an async rolling file appender.

**Observability:** Actuator health probes (`/actuator/health/liveness`,
`/readiness`), metrics (`/actuator/metrics`), cache stats. Next steps:
Micrometer → Prometheus, OpenTelemetry tracing (it propagates the correlation ID
for you).

---

## 13. Working style: asking the right questions, ADRs

Senior engineers are hired as much for the questions they ask as for the code
they write. For any airline feature, ask:

1. **Who is the channel?** Web, mobile, call centre, GDS, NDC partner? It changes latency, auth and API style.
2. **What's the invariant?** (No oversell, ticket matches PNR, refunds ≤ paid.)
3. **What's the consistency need?** Is "eventually" OK? For how long?
4. **What's the peak load?** Flash sales, IRROPS days, schedule-change batches.
5. **What happens on failure halfway?** Retries, idempotency keys, compensation.
6. **What's the data lifecycle?** Retention, PII, GDPR/regulatory deletion, audit.
7. **How will we know it works in production?** Metrics, alerts, dashboards.
8. **What's explicitly out of scope?**

**ADR template** (`docs/adr/NNNN-title.md`):
```
# NNNN. <Decision title>
Status: Proposed | Accepted | Superseded by NNNN
## Context        — forces, constraints, numbers
## Options        — 2–3 real alternatives, with pros/cons
## Decision       — what and why
## Consequences   — what gets easier, what gets harder, follow-ups
```

---

## 14. Production readiness checklist

- [ ] Secrets from a vault/KMS; dev JWT secret impossible in prod (fail startup if default).
- [ ] MongoDB: replica set, auth + TLS, least-privilege app user, backups tested by restoring.
- [ ] Indexes created by reviewed scripts (`auto-index-creation: false`).
- [ ] Schema validation enabled on critical collections.
- [ ] Seat-hold reconciliation job and alert on `Failed to release` log lines.
- [ ] Idempotency key on `POST /bookings`.
- [ ] Rate limiting on public search.
- [ ] Distributed cache if more than one instance.
- [ ] Graceful shutdown + readiness probe wired to the load balancer.
- [ ] Dashboards: p95 latency per endpoint, 4xx/5xx rate, booking rate, oversell = 0.
- [ ] Quality gate green, no critical Sonar issues, dependency vulnerability scan (OWASP / Dependabot).

---

## 15. How the job-spec keywords map to this repo

| Keyword | Where |
|---|---|
| Core Java, Java 17 | `pom.xml` (`java.version` 17), records, sealed types, switch expressions; Module 1 |
| Streams, lambdas | Services + `StreamsAndLambdasKataTest` |
| SOLID | §4.1 |
| Exception handling | `common/error`, `BookingService` compensation |
| Logging, SLF4J | Every class, `logback-spring.xml`, MDC filter |
| Spring Boot, Spring REST | `backend/` |
| Spring Security, JWT | `auth/` |
| NoSQL, MongoDB, filters, updates | `flight/FlightSearchQueryBuilder`, `FlightInventoryRepository`, `mongo/*.js`, `04-mongodb-mastery.md` |
| "Filters" (servlet) | `CorrelationIdFilter`, `RequestLoggingFilter`, `JwtAuthenticationFilter` |
| Caching | `CacheConfig`, `@Cacheable/@CacheEvict` |
| CI/CD, Jenkins, Groovy | `Jenkinsfile`, `ci/jenkins/shared-library` |
| "Groovy sparc framework" | Interpreted as **Spock** (the Groovy testing framework): `FarePricingSpec.groovy`. If the role actually means Spark Java (the micro web framework), the REST concepts transfer directly; ask the hiring manager to confirm. |
| JUnit | `src/test/java` |
| "Playwriting" | **Playwright**: `frontend/e2e` |
| gRPC, "protobug" | **Protocol Buffers**: `src/main/proto`, `grpc/` |
| Channel stubs | gRPC `ManagedChannel` + generated stubs (`FlightAvailabilityClient`, in-process channel in tests); also "sales channels" in the airline sense (§1) |
| IntelliJ | §3 IntelliJ tips |
| Bruno, Postman | `api-clients/` |
| Tomcat | §6.3–6.4, `ServletInitializer` |
| Sonar quality, updating rules | §8.3–8.4, `ci/sonar/update-quality-profile.sh` |
| System design | §2, `01-job-description.md` interview prompt, Module 14 capstone |
| React | `frontend/` |
