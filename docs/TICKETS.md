# Tickets

Each ticket is scoped to be doable independently (mostly) in one PR. "Required unit tests" are the minimum bar — see `TESTING_STANDARDS.md` for naming/structure. Ticket IDs are referenced in commit messages and PR titles per `AGENTS.md`.

---

### TICKET-001 — Project bootstrap & Docker Compose
**Status:** Complete (verified 2026-09-10).

**Verification:** `mvn clean verify` passes all 18 tests, including context startup
and HTTP health against Testcontainers PostgreSQL. `docker compose up --build -d`
starts both services and `/actuator/health` returns HTTP 200 with status `UP`.
Compose was checked in an isolated project with `POSTGRES_PORT=25432` and
`APP_PORT=18080` because the default host ports were occupied.

**Goal:** Buildable skeleton: Maven project (Java 25, Spring Boot 4+), Docker Compose with `app` + `postgres`, Actuator health check wired up.
**Acceptance criteria**
- `mvn clean verify` succeeds on an empty skeleton.
- `docker compose up --build` starts Postgres + app; `GET /actuator/health` returns `200 {"status":"UP"}`.
- `.env.example` documents required env vars.
**Required tests**
- `UrlShortenerApplicationTests.contextLoads()` — Spring context starts.
- (No business logic yet — this ticket is infra only.)

---

### TICKET-002 — Domain model & Flyway migration
**Status:** Complete (verified 2026-09-10).

**Implementation:** Existing `ShortUrl` and V1 migration match the required schema.
Added `ShortUrlRepository.findByShortCode`, entity documentation, five entity unit
tests, and five repository tests against Testcontainers PostgreSQL. Repository
tests apply Flyway migrations with Hibernate schema validation and verify generated
IDs, field persistence, lookups, duplicate-code rejection, and persisted analytics.
Added test-scoped `spring-boot-starter-data-jpa-test` and
`spring-boot-starter-flyway-test` for Spring Boot 4 JPA slices with Flyway.

**Verification:** `mvn clean verify` passes all 28 tests with no failures or skips.

**Goal:** `ShortUrl` JPA entity + `V1__create_short_url_table.sql` migration matching `ARCHITECTURE.md` §4.
**Acceptance criteria**
- Migration creates `short_url` table with unique index on `short_code`.
- `ShortUrl` entity maps all columns; no setters beyond what JPA needs (`clickCount`, `lastAccessedAt`).
**Required tests**
- `ShortUrlRepositoryTest` (`@DataJpaTest` + Testcontainers):
  - `save_validShortUrl_persistsAndGeneratesId()`
  - `findByShortCode_existingCode_returnsEntity()`
  - `findByShortCode_unknownCode_returnsEmptyOptional()`
  - `save_duplicateShortCode_throwsDataIntegrityViolationException()`

---

### TICKET-003 — Base62 short code generator
**Status:** Complete (verified 2026-09-10).

**Implementation:** Stateless encoding uses `0-9A-Za-z`, maps zero to `0`, and
rejects negative IDs. Decoding rejects null/blank input, invalid characters, and
values exceeding `Long.MAX_VALUE` before arithmetic can overflow. Leading zeroes
are accepted. Tests cover known values, numeric boundaries, 100 seeded random
round-trips, deterministic alphabet-only output, and invalid input.

**Verification:** Three overflow regression cases failed before the fix.
`mvn clean verify` now passes all 161 tests, including 149 Base62 cases, with no
failures or skips.

**Class:** `util.Base62Encoder`
**Goal:** Stateless encoder: `String encode(long id)` and `long decode(String code)`.
**Acceptance criteria**
- Encoding is deterministic and round-trips (`decode(encode(x)) == x`).
- Output uses only `[0-9A-Za-z]`.
- Negative/zero ids handled explicitly (zero → defined output, negative → `IllegalArgumentException`).
**Required tests** (`Base62EncoderTest`)
- `encode_zero_returnsExpectedBaseChar()`
- `encode_positiveNumber_returnsExpectedString()` (parameterized over a few known values)
- `encode_negativeNumber_throwsIllegalArgumentException()`
- `decode_validCode_returnsOriginalNumber()`
- `encodeThenDecode_randomValues_roundTrips()` (property-style parameterized test)
- `decode_invalidCharacter_throwsIllegalArgumentException()`

---

### TICKET-004 — Create short URL (core happy path)
**Status:** Complete (verified 2026-09-10).

**Implementation:** Added request/response records, a transactional service, and
`POST /api/v1/urls` returning HTTP 201 with the public link in `Location`. The
service validates HTTP/HTTPS destinations and the configured maximum length,
persists creation metadata, and replaces an internal temporary code with the
generated ID's Base62 encoding within the transaction. The repository performs
the code update without adding entity setters. Non-null alias/expiry options are
rejected until TICKET-005/006. Unreadable JSON returns HTTP 400 ProblemDetail.
Added test-scoped `spring-boot-starter-webmvc-test` for Boot 4 MVC slice tests.
Updated README, API requests, and architecture documentation.

**Verification:** `mvn clean verify` passes all 183 tests, including mocked service
and encoder tests, MVC tests, and a real HTTP creation test that independently
reads the committed PostgreSQL row and verifies its ID-derived code.

**Class:** `service.UrlShortenerServiceImpl`, method `create(CreateShortUrlRequest)`
**Goal:** Persist a new `ShortUrl`; auto-generate code via `Base62Encoder` when no custom alias given.
**Acceptance criteria**
- Valid URL, no alias → entity saved, code generated from new id, response DTO returned.
- Invalid URL format → `InvalidUrlException`.
**Required tests** (`UrlShortenerServiceImplTest`, repository + encoder mocked)
- `create_validUrlNoAlias_savesEntityWithGeneratedCode()`
- `create_malformedUrl_throwsInvalidUrlException()`
- `create_blankUrl_throwsInvalidUrlException()`
- `create_urlExceedingMaxLength_throwsInvalidUrlException()` (define and enforce a max, e.g. 2048 chars)
- `create_persistsCreatedAtTimestamp()`

**Controller:** `controller.UrlController#createShortUrl`
**Required tests** (`UrlControllerTest`, `@WebMvcTest`, service mocked)
- `createShortUrl_validRequest_returns201WithLocationAndBody()`
- `createShortUrl_missingOriginalUrl_returns400()`
- `createShortUrl_serviceThrowsInvalidUrlException_returns400WithProblemDetail()`

---

### TICKET-005 — Custom alias support
**Status:** Complete (verified 2026-09-10).

**Implementation:** Validates aliases at the DTO and service boundaries, preserves
case and exact spelling, and persists `custom_alias=true` without Base62 encoding.
Uses the permitted `InvalidUrlException` option for invalid aliases (the test names
below therefore use `throwsInvalidUrlException`). Existing codes and concurrent
unique-index conflicts raise `DuplicateAliasException` (HTTP 409). No schema or
library changes. Expiry remains unsupported until TICKET-006.

**Verification:** `mvn verify` passes all 206 tests with no failures or skips.
Coverage includes valid boundary lengths, blank/invalid aliases, existing and
concurrently claimed aliases, HTTP 400/409 responses, and an HTTP/PostgreSQL test
that verifies committed alias metadata and rejection of a repeated request.

**Extends:** `UrlShortenerServiceImpl.create(...)`
**Goal:** When `customAlias` is present, validate pattern and uniqueness instead of generating a code.
**Acceptance criteria**
- Alias matching `^[a-zA-Z0-9_-]{3,16}$` and unused → saved as-is, `customAlias=true`.
- Alias failing pattern → `InvalidUrlException` (or a dedicated `InvalidAliasException` — pick one and be consistent).
- Alias already taken → `DuplicateAliasException`.
**Required tests**
- `create_validCustomAlias_savesWithGivenCode()`
- `create_aliasTooShort_throwsInvalidUrlException()`
- `create_aliasTooLong_throwsInvalidUrlException()`
- `create_aliasWithInvalidCharacters_throwsInvalidUrlException()`
- `create_aliasAlreadyTaken_throwsDuplicateAliasException()`
**Controller tests**
- `createShortUrl_duplicateAlias_returns409()`
- `createShortUrl_invalidAliasPattern_returns400()`

---

### TICKET-006 — Expiration support
**Status:** Creation acceptance criteria complete (verified 2026-09-10).

**Implementation:** Optional expiry is validated at the DTO and service boundaries.
Past or present timestamps are rejected; direct service calls raise
`InvalidUrlException` with `expiresAt must be in the future`. Generated codes and
custom aliases both persist and return future expiry; omitted/null expiry remains
null (no expiration). The existing column is reused without a schema change.
Resolve-time enforcement is covered by TICKET-007, which introduces the resolver.

**Verification:** `mvn verify` passes all 217 tests with no failures or skips.
Tests cover past, present, future, and null expiry, HTTP validation/serialization,
and committed PostgreSQL expiry for generated codes and custom aliases.

**Goal:** Optional `expiresAt` on create; enforced on resolve.
**Acceptance criteria**
- `expiresAt` in the past at creation time → `InvalidUrlException` ("expiresAt must be in the future").
- `expiresAt` null → link never expires.
**Required tests**
- `create_expiresAtInPast_throwsInvalidUrlException()`
- `create_expiresAtInFuture_savesSuccessfully()`
- `create_noExpiresAt_savesWithNullExpiry()`

---

### TICKET-007 — Resolve / redirect
**Status:** Complete (verified 2026-09-10).

**Implementation:** Added transactional `resolve` and `GET /{shortCode}` returning
302 with `Location`, an empty body, and `Cache-Control: no-store`. Unknown codes
return 404; expired links return 410 without modifying analytics. A pessimistic
row lock serializes access updates so concurrent redirects preserve every click.
Analytics are committed synchronously before redirecting; the earlier architecture
diagram's async increment was updated to match the ticket requirements.

**Verification:** `mvn verify` passes all 230 tests with no failures or skips.
Includes all required service/controller cases, repository lock lookups, eight
concurrent HTTP redirects with persisted click counts, and an expired HTTP request
that leaves PostgreSQL analytics unchanged. No schema or dependency changes.

**Class:** `service.UrlShortenerServiceImpl`, method `resolve(String shortCode)`
**Goal:** Look up by code, check expiry, increment click count + last-accessed, return original URL.
**Acceptance criteria**
- Unknown code → `UrlNotFoundException`.
- Expired code → `UrlExpiredException`, click count NOT incremented.
- Valid, non-expired code → returns original URL, click count incremented, `lastAccessedAt` updated.
**Required tests**
- `resolve_unknownCode_throwsUrlNotFoundException()`
- `resolve_expiredCode_throwsUrlExpiredExceptionAndDoesNotIncrementClickCount()`
- `resolve_validCode_returnsOriginalUrl()`
- `resolve_validCode_incrementsClickCount()`
- `resolve_validCode_updatesLastAccessedAt()`

**Controller:** `controller.RedirectController#redirect`
**Required tests**
- `redirect_validCode_returns302WithLocationHeader()`
- `redirect_unknownCode_returns404()`
- `redirect_expiredCode_returns410()`

---

### TICKET-008 — Stats endpoint
**Class:** `service.UrlShortenerServiceImpl`, method `getStats(String shortCode)`
**Goal:** Return click count, timestamps, original URL — without incrementing click count (distinct from `resolve`).
**Acceptance criteria**
- Unknown code → `UrlNotFoundException`.
- Existing code → full stats DTO, no side effects on the entity.
**Required tests**
- `getStats_unknownCode_throwsUrlNotFoundException()`
- `getStats_existingCode_returnsCorrectStatsDto()`
- `getStats_existingCode_doesNotIncrementClickCount()`
**Controller tests**
- `getStats_validCode_returns200WithBody()`
- `getStats_unknownCode_returns404()`

---

### TICKET-009 — Delete short URL
**Class:** `service.UrlShortenerServiceImpl`, method `delete(String shortCode)`
**Acceptance criteria**
- Unknown code → `UrlNotFoundException`.
- Existing code → row deleted, idempotent delete-again returns 404 (not 204).
**Required tests**
- `delete_unknownCode_throwsUrlNotFoundException()`
- `delete_existingCode_removesEntity()`
**Controller tests**
- `deleteShortUrl_validCode_returns204()`
- `deleteShortUrl_unknownCode_returns404()`

---

### TICKET-010 — Global exception handling
**Class:** `exception.GlobalExceptionHandler`
**Goal:** Map every domain exception + validation failure to the `ProblemDetail` shapes in `ARCHITECTURE.md` §6.
**Required tests** (`GlobalExceptionHandlerTest`, can be a focused `@WebMvcTest` or direct unit test of the handler methods)
- `handleInvalidUrlException_returns400ProblemDetail()`
- `handleDuplicateAliasException_returns409ProblemDetail()`
- `handleUrlNotFoundException_returns404ProblemDetail()`
- `handleUrlExpiredException_returns410ProblemDetail()`
- `handleMethodArgumentNotValid_returns400WithFieldErrors()`
- `handleUnexpectedException_returns500WithoutLeakingStackTrace()`

---

### TICKET-011 — OpenAPI / Swagger documentation
**Goal:** springdoc-openapi wired up; every endpoint annotated with summary/response codes; matches `API_REQUESTS.md`.
**Acceptance criteria**
- `/swagger-ui.html` and `/v3/api-docs` reachable and reflect actual endpoints.
**Required tests**
- `OpenApiConfigTest` (or slice test) — `getApiDocs_returns200AndContainsAllEndpoints()` (light smoke test; heavy assertion on doc content is not required).

---

### TICKET-012 — Dockerization & CI
**Goal:** Multi-stage `Dockerfile` (Maven build stage → slim JRE runtime), GitHub Actions workflow running `mvn clean verify` on every PR.
**Acceptance criteria**
- Image builds and runs standalone against the `postgres` service in `docker-compose.yml`.
- CI fails the PR if `mvn clean verify` fails.
**Required tests**
- N/A (infra ticket) — acceptance verified via CI run + manual `docker compose up`.

---

### TICKET-013 — Observability
**Goal:** Structured JSON logging in `docker` profile, Actuator `/metrics` exposed, key business events logged (`info`) per `CODING_STANDARDS.md`.
**Required tests**
- `UrlShortenerServiceImplTest#create_validRequest_logsCreationEvent()` (using a log-capturing appender, e.g. Logback `ListAppender`) — optional but recommended, not blocking.

---

## Future / backlog (not required for v1, do not implement without a new ticket + ARCHITECTURE.md update)
- **TICKET-F01** — API key auth + per-user URL ownership.
- **TICKET-F02** — Redis cache in front of `resolve()`.
- **TICKET-F03** — Rate limiting (Bucket4j) on `POST /api/v1/urls`.
- **TICKET-F04** — Bulk shorten endpoint (`POST /api/v1/urls/bulk`).
- **TICKET-F05** — QR code generation for a short URL.
- **TICKET-F06** — Link-safety/malware scanning integration before accepting a URL.
