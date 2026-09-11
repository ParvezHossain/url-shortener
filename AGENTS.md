# AGENTS.md

This file tells any AI coding agent (Claude Code, Copilot, Cursor, etc.) — or a human filling in for the owner — how to work in this repository safely and consistently. Read this before writing any code.

## 1. Project in one paragraph
A self-hosted URL shortener built with Java 25 and Spring Boot 4+. It exposes a REST API to shorten URLs (with optional custom alias and expiry), redirect short codes to their original URL, and report click analytics. Persistence is PostgreSQL via Spring Data JPA + Flyway migrations. Fully containerized with Docker Compose. See `docs/PROJECT_OVERVIEW.md` for the why, `docs/ARCHITECTURE.md` for the how.

## 2. Golden rules
1. **Never break the build.** Run `mvn verify` before considering any task done.
2. **Every public method gets a unit test.** No ticket is "done" without tests (see `docs/TICKETS.md` for the required test list per ticket).
3. **No business logic in controllers.** Controllers only: validate input shape (via `@Valid` DTOs), call a service, map to a response DTO. All logic lives in `service/`.
4. **Entities never leave the service layer.** Controllers speak DTOs only (`dto/request`, `dto/response`). Never return a JPA `@Entity` from a controller.
5. **Fail with meaningful exceptions.** Use the custom exceptions in `exception/` (`UrlNotFoundException`, `InvalidUrlException`, `DuplicateAliasException`, `UrlExpiredException`). Never let a raw `RuntimeException`/`SQLException` bubble to the client — `GlobalExceptionHandler` maps them to RFC 7807 `ProblemDetail` responses.
5. **Schema changes go through Flyway.** Never use `spring.jpa.hibernate.ddl-auto=update` outside local scratch work. Add a new `V{n}__description.sql` file under `src/main/resources/db/migration`.
6. **Config via environment variables**, never hardcoded secrets. See `.env.example` / `application.yml` placeholders.
7. **Keep commits/PRs scoped to one ticket.** Reference the ticket ID (e.g. `TICKET-004`) in the commit message and PR title.

## 3. How to work a ticket
1. Open `docs/TICKETS.md`, find the ticket, read its acceptance criteria and required unit tests.
2. Check `docs/ARCHITECTURE.md` for where the new code belongs (package + layer).
3. Write the test list first (or alongside), following `docs/TESTING_STANDARDS.md` naming conventions.
4. Implement the minimum code to pass the tests.
5. Run `mvn verify` locally (unit + integration tests, checkstyle if configured).
6. Update `README.md` / `docs/API_REQUESTS.md` if you added or changed an endpoint.
7. Open a PR referencing the ticket ID; fill in what changed and why.

## 4. Commands an agent should know
```bash
# Build & run all tests
mvn clean verify

# Run only unit tests (fast, no DB)
mvn test -Dgroups=unit

# Run only integration tests (Testcontainers, needs Docker)
mvn verify -Dgroups=integration

# Run the app locally with dependencies
docker compose up -d postgres
mvn spring-boot:run

# Full stack (app + db) in containers
docker compose up --build

# Apply/check DB migrations manually
mvn flyway:migrate
mvn flyway:info
```

## 5. Directory map (see ARCHITECTURE.md for details)
```
src/main/java/com/parvez/urlshortener/
  controller/   REST endpoints (thin)
  service/      business logic, interfaces + impl
  repository/   Spring Data JPA repositories
  domain/       JPA entities
  dto/          request/response records
  exception/    custom exceptions + GlobalExceptionHandler
  config/       Spring configuration (OpenAPI, caching, rate limiting)
  util/         stateless helpers (e.g. Base62 encoder)
src/main/resources/db/migration/   Flyway SQL migrations, V1__..., V2__...
src/test/java/...                  mirrors main/java package-for-package
docs/                               all project documentation
```

## 6. Coding standards (full detail in docs/CODING_STANDARDS.md)
- Java 25, records for DTOs, constructor injection only (no field `@Autowired`).
- Package-by-layer as shown above (not package-by-feature) — keep it consistent.
- Null-safety: no method returns `null`; use `Optional<T>` at repository boundary, throw domain exceptions in the service layer.
- Every class and public method has a one-line Javadoc stating intent, not restating the signature.
- Logging via SLF4J (`private static final Logger log = LoggerFactory.getLogger(X.class)`), never `System.out`.

## 7. Testing standards (full detail in docs/TESTING_STANDARDS.md)
- Framework: JUnit 6 + Mockito + AssertJ.
- Naming: `methodName_condition_expectedResult()`.
- One test class per production class: `UrlShortenerServiceTest` for `UrlShortenerService`.
- Services: pure unit tests, dependencies mocked.
- Repositories: `@DataJpaTest` with Testcontainers Postgres.
- Controllers: `@WebMvcTest` with `MockMvc`, service layer mocked.
- No test may depend on execution order or on another test's side effects.

## 8. When context is missing (prompts for the owner's absence)
If you (the agent) are picking up work without the owner available, and something is ambiguous:
- Default to the simplest solution consistent with `docs/ARCHITECTURE.md`.
- Do not introduce a new library/framework not already in `pom.xml` without flagging it clearly in the PR description.
- If a ticket's acceptance criteria conflict with existing code, prefer the ticket and note the conflict in the PR.
- Never guess at business rules affecting money, auth, or data deletion — leave a `TODO(owner):` comment and stop, instead of assuming.

## 9. Good prompts to give an agent on this repo
- "Implement TICKET-005 exactly as specified in docs/TICKETS.md, including all listed unit tests."
- "Add a new Flyway migration to add a `custom_alias` boolean column to short_url, and update ShortUrl entity + repository + relevant tests."
- "Review PR diff against docs/CODING_STANDARDS.md and docs/TESTING_STANDARDS.md and list violations."
- "Explain the redirect flow end-to-end using docs/ARCHITECTURE.md and the current controller/service code."
