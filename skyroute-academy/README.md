# SkyRoute Academy

A hands-on learning kit for the **Senior Java Engineer (airline platforms)**
role. It contains a job description to hire with, a course to learn from, and a
working airline reservation app that uses every technology in the job spec.

> This folder is self-contained and independent of the PR-reviewer project in
> the rest of this repository. The repo's `AGENTS.md` conventions don't apply here.

## What's inside

| Path | What |
|---|---|
| [`docs/01-job-description.md`](docs/01-job-description.md) | Job description, plus an interviewer kit (questions, a system design prompt, a scorecard) |
| [`docs/02-course-outline.md`](docs/02-course-outline.md) | 14-module course, each module with labs mapped to this code |
| [`docs/03-engineering-manual.md`](docs/03-engineering-manual.md) | How and why the app is built: domain, architecture, security, CI/CD, gRPC, testing |
| [`docs/04-mongodb-mastery.md`](docs/04-mongodb-mastery.md) | MongoDB from zero to expert, with a 30-day plan |
| `backend/` | Java 17 · Spring Boot 3 · Spring Security + JWT · MongoDB · REST + gRPC/protobuf · Caffeine cache · SLF4J/Logback · JUnit 5 · Mockito · Spock · Testcontainers · JaCoCo/Sonar |
| `frontend/` | React + TypeScript (Vite) · Playwright end-to-end tests |
| `mongo/` | Index script, `$jsonSchema` validation, 17 graded `mongosh` exercises |
| `api-clients/` | Bruno collection, Postman collection, grpcurl recipes |
| `Jenkinsfile`, `ci/` | Declarative pipeline, Groovy shared library, Sonar quality-profile-as-code script |
| `docker-compose.yml` | MongoDB replica set + mongo-express + API |

## Quick start

```bash
docker compose up -d mongo mongo-express          # MongoDB on :27017, UI on :8081
cd backend && mvn spring-boot:run                 # REST :8080, gRPC :9090, seeds demo data
cd ../frontend && npm install && npm run dev      # http://localhost:5173
```

Log in as `agent / agent123` or `admin / admin123`. Then open the Bruno
collection in `api-clients/bruno/SkyRoute` and run the requests in order.

## Tests

```bash
cd backend && mvn test        # unit, web-slice, Spock, gRPC in-process tests
cd backend && mvn verify      # + Testcontainers integration tests against real MongoDB (needs Docker)
cd frontend && npm run test:e2e
```

## Where to start

1. Read the domain primer (Manual §1) and run the app.
2. Follow the course from Module 0. Each module tells you which files to read and what to build.
3. Spend the most time on Module 8 and the MongoDB guide.
