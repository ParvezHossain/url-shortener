# Tickets

Each ticket is scoped to be doable independently (mostly) in one PR. "Required unit tests" are the minimum bar — see `TESTING_STANDARDS.md` for naming/structure. Ticket IDs are referenced in commit messages and PR titles per `AGENTS.md`.

---

### TICKET-001 — Project bootstrap & Docker Compose
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
**Extends:** `UrlShortenerServiceImpl.create(...)`
**Goal:** When `customAlias` is present, validate pattern and uniqueness instead of generating a code.
**Acceptance criteria**
- Alias matching `^[a-zA-Z0-9_-]{3,16}$` and unused → saved as-is, `customAlias=true`.
- Alias failing pattern → `InvalidUrlException` (or a dedicated `InvalidAliasException` — pick one and be consistent).
- Alias already taken → `DuplicateAliasException`.
**Required tests**
- `create_validCustomAlias_savesWithGivenCode()`
- `create_aliasTooShort_throwsInvalidAliasException()`
- `create_aliasTooLong_throwsInvalidAliasException()`
- `create_aliasWithInvalidCharacters_throwsInvalidAliasException()`
- `create_aliasAlreadyTaken_throwsDuplicateAliasException()`
**Controller tests**
- `createShortUrl_duplicateAlias_returns409()`
- `createShortUrl_invalidAliasPattern_returns400()`

---

### TICKET-006 — Expiration support
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
