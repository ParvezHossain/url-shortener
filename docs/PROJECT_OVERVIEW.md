# Project Overview — What This Is

## What it is
A self-hosted, production-shaped **URL shortener** service. It takes a long URL and returns a short, shareable code that redirects to it — like bit.ly or TinyURL, but self-hosted, with source you own and can extend.

## What it does (functional scope)
- Shorten a long URL into a short code (auto-generated, Base62).
- Optionally accept a **custom alias** instead of an auto-generated code.
- Redirect `GET /{code}` → 302 to the original URL.
- Optional **expiration** on a short URL (`expiresAt`); expired links return 410 Gone.
- **Click analytics**: count of redirects, last-accessed timestamp, per short code.
- Delete a short URL.
- OpenAPI/Swagger docs for the API.

## What it deliberately does NOT do (v1 scope)
- No user accounts / auth in v1 (all URLs are anonymous). Flagged as a future ticket.
- No UI/frontend — API only in v1.
- No link-preview/malware scanning in v1.
- No horizontal scaling / distributed ID generation concerns in v1 (single Postgres instance is the source of truth).

These are intentionally deferred so the core can be built, tested, and understood end-to-end first — see `docs/TICKETS.md` for the backlog of "future" tickets covering these.

## Who this is for
- **The owner**: a portfolio/learning project to practice clean Spring Boot 4 architecture, testing discipline, and Docker packaging.
- **An AI agent** picking up work in the owner's absence: `AGENTS.md` is the entry point; this file plus `ARCHITECTURE.md` and `TICKETS.md` give enough context to implement any ticket without further clarification in most cases.
- **A future contributor / reviewer**: `README.md` gets them running locally in minutes; `docs/CODING_STANDARDS.md` and `docs/TESTING_STANDARDS.md` explain the conventions so a PR looks like it belongs.

## Success criteria for v1
- `docker compose up --build` gives a fully working API with zero manual steps.
- Every service/util class has unit tests; every endpoint has an integration/controller test.
- `mvn clean verify` is green and is the single gate for "done."
- All endpoints documented in `docs/API_REQUESTS.md` and reflected in generated OpenAPI/Swagger UI.

## Non-goals / explicit constraints
- Keep the dependency footprint minimal — Spring Boot Web, Data JPA, Validation, Flyway, PostgreSQL driver, OpenAPI (springdoc), plus Testcontainers/JUnit/Mockito/AssertJ for tests. Don't add a library without updating `AGENTS.md`'s golden rules note about flagging new dependencies.
