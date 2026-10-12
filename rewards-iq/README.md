# Rewards IQ (work in progress)

Backend for Rewards IQ, built from the requirements spec: airline analysts manage loyalty points
redemption rules over REST, approved rules are published to the Dynamic Pricing Engine (DPE)
through a transactional outbox, and DPE prices offers in points over gRPC.

Stack: Java 17, Spring Boot 3, Spring Security (JWT resource server), MongoDB, gRPC/protobuf.

## Status

Compiles (`mvn -DskipTests compile` from `backend/`). Not runnable yet.

Done:
- Rule lifecycle: create, edit, new version, clone, submit, approve (maker-checker), reject, retire
- Business-rule validation (BR-01..BR-09) and publish-time conflict checks
- REST API `/api/v1/rules` with ETag / If-Match, history endpoint, preview endpoint
- JWT security with roles and airline tenancy; dev-only token endpoint
- Pricing engine: points calculator (BR-03/BR-04), rule index and in-memory rules cache
- Outbox worker with retry/backoff and a gRPC DPE publisher
- Protobuf contracts in `backend/src/main/proto`

Still to do:
- Plan B batch CSV export
- gRPC pricing server and service-token auth
- Local stub DPE
- `application.yml`, docker-compose
- Tests (JUnit, Spock, Testcontainers) and API collections
