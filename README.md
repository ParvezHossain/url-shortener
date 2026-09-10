# URL Shortener

A self-hosted URL shortener API built with Java 25, Spring Boot 4+, PostgreSQL, and Docker.

> New here? Read `docs/PROJECT_OVERVIEW.md` for what/why, `docs/ARCHITECTURE.md` for how it's built, and `AGENTS.md` if you're an AI agent (or a human) picking up tickets.

## Features

Current API: `POST /api/v1/urls` validates an HTTP/HTTPS destination and returns a
created short link with an ID-based Base62 code or a custom alias. Aliases are
case-sensitive and must match `[a-zA-Z0-9_-]{3,16}`; invalid aliases return HTTP 400
and occupied aliases return HTTP 409. An optional future `expiresAt` timestamp is
saved and returned; omit it or set it to `null` for no expiry. Past or present expiry
returns HTTP 400. `GET /{shortCode}` redirects active links with HTTP 302 and records click count
and last access. Unknown links return 404; expired links return 410 without
changing analytics. Stats remain planned. See `docs/API_REQUESTS.md` for the implemented contract.

Planned full feature set:
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
cp .env.example .env
set -a
. ./.env
set +a
docker compose up -d postgres
mvn spring-boot:run
```

Compose reads `.env` automatically; Maven needs the variables exported as above.
If host ports are occupied, change `POSTGRES_PORT` / `APP_PORT` in `.env`.
For a host-run app, also update `POSTGRES_URL` to match `POSTGRES_PORT`.
Set `APP_BASE_URL` to the app's public origin. The containerized app always
connects to `postgres:5432` inside the Compose network.

Check startup with `curl --fail http://localhost:8080/actuator/health`
(adjust the port when using `APP_PORT`). Expect HTTP 200 and status `UP`.

### Run tests
```bash
mvn clean verify           # full suite (unit + integration, needs Docker for Testcontainers)
mvn test -Dgroups=unit     # unit tests only, fast, no Docker
```

The bootstrap integration tests start an isolated PostgreSQL container on a random
port using Testcontainers. A running Docker daemon is required; a pre-existing
database or `.env` file is not required for `mvn clean verify`.

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
