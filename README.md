# URL Shortener

A self-hosted URL shortener API built with Java 25, Spring Boot 4+, PostgreSQL, and Docker.

> New here? Read `docs/PROJECT_OVERVIEW.md` for what/why, `docs/ARCHITECTURE.md` for how it's built, and `AGENTS.md` if you're an AI agent (or a human) picking up tickets.

## Features
- Shorten a URL to a short code (auto-generated, Base62) or a custom alias.
- Optional expiration on links.
- Redirect endpoint with click analytics.
- OpenAPI/Swagger docs.
- Fully containerized (Docker Compose: app + PostgreSQL).

## Tech stack
| | |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4+ (Web, Data JPA, Validation, Actuator) |
| Database | PostgreSQL |
| Migrations | Flyway |
| Build | Maven 3.9+ |
| Testing | JUnit 6, Mockito, AssertJ, Testcontainers |
| Docs | springdoc-openapi |
| Container | Docker / Docker Compose |

## Quick start

### Run everything in Docker
```bash
git clone <repo-url> && cd url-shortener
cp .env.example .env
docker compose up --build
```
API is now at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Run locally (DB in Docker, app on host)
```bash
docker compose up -d postgres
mvn spring-boot:run
```

### Run tests
```bash
mvn clean verify           # full suite (unit + integration, needs Docker for Testcontainers)
mvn test -Dgroups=unit     # unit tests only, fast, no Docker
```

## Example usage
```bash
# Create a short URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/long/path"}'

# Follow the short link
curl -i http://localhost:8080/<shortCode>

# Get stats
curl http://localhost:8080/api/v1/urls/<shortCode>
```
Full request/response reference: `docs/API_REQUESTS.md`.

## Project documentation
| File | Contents |
|---|---|
| `docs/PROJECT_OVERVIEW.md` | What this project is and isn't, scope, success criteria |
| `docs/ARCHITECTURE.md` | Layered architecture, data model, error handling, deployment |
| `docs/API_REQUESTS.md` | Every endpoint, request/response examples, error shapes |
| `docs/TICKETS.md` | Full backlog, one ticket per feature, with required unit tests |
| `docs/CODING_STANDARDS.md` | Java/Spring conventions used across the codebase |
| `docs/TESTING_STANDARDS.md` | Test stack, naming, coverage expectations |
| `AGENTS.md` | How an AI coding agent (or stand-in human) should work this repo |

## Contributing / picking up a ticket
1. Pick an open ticket from `docs/TICKETS.md`.
2. Follow the workflow in `AGENTS.md` §3.
3. `mvn clean verify` must pass before opening a PR.
4. Reference the ticket ID in the commit message and PR title.

## License
MIT (adjust as needed).
