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
**Status:** Complete (verified 2026-09-10).

**Implementation:** Added `ShortUrlStatsResponse`, read-only `getStats`, and
`GET /api/v1/urls/{shortCode}` returning HTTP 200 with all documented metadata
and analytics. Existing expired links remain queryable; unknown codes return 404.
Reads do not call the resolver or modify click count or last-access time.

**Verification:** `mvn verify` passes all 239 tests with no failures or skips.
Includes required service/controller cases, expired and unvisited links, nullable
response fields, and repeated HTTP stats reads with independent PostgreSQL checks
confirming analytics remain unchanged. No schema or dependency changes.

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
**Status:** Complete (verified 2026-09-10).

**Implementation:** Added transactional `delete` and
`DELETE /api/v1/urls/{shortCode}` returning HTTP 204 with an empty body. Existing
links (including expired links) and their analytics are removed. Unknown codes
and repeated deletion return 404. Reuses the redirect row lock to coordinate
concurrent deletes and redirects. No schema or dependency changes.

**Verification:** `mvn verify` passes all 246 tests with no failures or skips.
Includes required service/controller cases, expired-link deletion, committed
PostgreSQL deletion, subsequent delete/redirect/stats returning 404, and concurrent
deletes returning exactly one 204 and one 404.

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
**Status:** Complete (verified 2026-09-10).

**Implementation:** Preserved the existing domain/validation status mappings and
centralized problem creation to explicitly set `type: about:blank`, which was
previously omitted. Added method documentation and dedicated HTTP tests covering
all five problem fields, field validation messages, unreadable request bodies,
and generic 500 responses without exception details. No new dependencies.

**Verification:** The nine new HTTP cases initially failed because `type` was
missing. After explicit initialization, `mvn verify` passes all 255 tests with
no failures or skips, including all required `GlobalExceptionHandlerTest` cases.

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
**Status:** Complete (verified 2026-09-10).

**Implementation:** Added `OpenApiConfig` metadata and shared problem response
schemas. All four controller operations now declare summaries, descriptions,
applicable response codes, success schemas, and redirect/creation headers.
Request schemas describe URL validation, optional aliases, and expiry. Reuses
the existing Springdoc dependency; disables generic advice-response inference
so endpoint documentation reflects the applicable errors.

**Verification:** `mvn verify` passes all 257 tests with no failures or skips.
`OpenApiConfigTest` checks all generated operations and response codes at
`/v3/api-docs`, shared problem schemas, and reachable Swagger UI HTML via
`/swagger-ui.html` against a running application with Testcontainers PostgreSQL.

**Goal:** springdoc-openapi wired up; every endpoint annotated with summary/response codes; matches `API_REQUESTS.md`.
**Acceptance criteria**
- `/swagger-ui.html` and `/v3/api-docs` reachable and reflect actual endpoints.
**Required tests**
- `OpenApiConfigTest` (or slice test) — `getApiDocs_returns200AndContainsAllEndpoints()` (light smoke test; heavy assertion on doc content is not required).

---

### TICKET-012 — Dockerization & CI
**Status:** Implemented and locally verified (2026-09-10); hosted CI run pending push.

**Implementation:** Retained the Maven build / Java 25 Alpine JRE stages, added
an unprivileged runtime user and an HTTP health check. Added GitHub Actions for
all PRs, main-branch pushes, and manual dispatch: Temurin 25, Maven cache,
`mvn --batch-mode --no-transfer-progress clean verify`, Compose build/start with
health waiting, HTTP health smoke check, failure logs, and runner cleanup.
Verification failures propagate to the job; no continue-on-error or test skips.

**Verification:** Clean Maven verification passes all 257 tests without failures
or skips. Workflow YAML parses and has the required PR trigger and verification
command. `docker compose up --build --wait --wait-timeout 180` succeeded in the
isolated `urlshortener-ticket012` project on ports 18080/25432. Both services became
healthy; HTTP create/redirect/stats/delete/OpenAPI checks passed, and runtime UID
was non-root. The temporary containers and volume were removed after testing.
A hosted GitHub Actions run has not been performed because the workflow has not
been pushed; that acceptance check remains pending.

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

## Frontend — v1

The v1 frontend is a responsive React + TypeScript application under `frontend/`, built with Vite and served by Spring Boot as static assets in production. It consumes the existing `/api/v1` API and does not introduce authentication or invent backend capabilities. Styling uses an application-owned design system based on CSS custom properties; any additional UI library requires an explicit architecture decision.

---

### TICKET-014 — Frontend foundation and professional design system
**Status:** Complete (verified 2026-09-11).

**Implementation:** Added React + TypeScript + Vite under `frontend/`, an
application-owned responsive design system, all eight shared UI primitives,
`AppShell`, and a persistent system-aware theme toggle. The foundation landing
page links to the existing Swagger UI; create/analytics workflows remain in
subsequent tickets. Added npm lint/format/test/build commands and a lockfile.
Maven uses the new `exec-maven-plugin` to build and check the frontend and package
its assets. Docker adds a Node 24 build stage; CI installs Node 24. A thin root
controller serves HTML directly because the default welcome-page forward would
collide with the existing short-code route. Updated development, architecture,
API, and testing documentation. No UI library or database changes.

**Verification:** `mvn clean verify` passes all 259 Java and 15 frontend tests
without failures or skips, including frontend lint/format checks and production
build. `docker build -t url-shortener:ticket014 .` succeeds.
Frontend tests cover navigation/footer, keyboard activation,
busy buttons, field accessibility, all UI primitives, persistent themes, OS
changes, and unavailable storage. A real HTTP/PostgreSQL smoke test verifies the
production HTML and referenced JS/CSS plus unchanged unknown-code 404 semantics.
Headless Chrome checks passed at 320, 390, 768, 1024, 1440, and 1920px without
horizontal overflow; verified dark-theme persistence, reduced motion, and native
modal background-focus exclusion, Escape dismissal, and focus restoration.
Text-token contrast is at least 5.32:1 in light mode and 7.21:1 in dark mode;
input/button boundary contrast meets 3:1.

**Depends on:** TICKET-012.

**Goal:** Bootstrap the frontend, establish the visual language, and integrate its production build with the Spring Boot application.

**Design direction:** A polished, trustworthy SaaS-style interface with a deep navy/slate foundation, an electric-blue accent, generous spacing, subtle elevation, crisp typography, and restrained motion. The interface must look intentional on mobile, tablet, laptop, and wide desktop screens.

**Component(s):** `AppShell`, `ThemeToggle`, `Button` (and the shared token/design-system layer they consume)
**Acceptance criteria**
- Create `frontend/` using React, TypeScript, and Vite with lint, format, test, and production-build commands.
- Add an application shell with responsive header, logo/wordmark, navigation, main content area, and footer.
- Define reusable tokens for color, typography, spacing, radius, elevation, focus rings, breakpoints, and motion.
- Provide reusable `Button`, `Input`, `Card`, `Alert`, `Spinner`, `Skeleton`, `Modal`, and `Toast` components.
- Support light and dark themes, defaulting to the operating-system preference and persisting the user's explicit choice.
- Use semantic HTML, visible keyboard focus, reduced-motion support, and WCAG 2.2 AA color contrast.
- Configure the local Vite development proxy for the Spring Boot API; do not hardcode production hosts.
- Integrate the frontend production build into Maven/Docker so the deployed application remains a single self-hosted app.
- Document local frontend development and production build commands in `README.md`.
  **Required tests**
- `AppShell_rendersPrimaryNavigationAndFooter()`
- `ThemeToggle_userChangesTheme_persistsPreference()`
- `Button_keyboardActivation_invokesAction()`
- `frontendProductionBuild_isServedBySpringBoot()` (integration smoke test)

---

### TICKET-015 — Responsive create-short-link experience
**Status:** Complete (verified 2026-09-11).

**Implementation:** Replaced the illustrative hero card with `CreateUrlForm`.
The form validates HTTP/HTTPS destinations, optional aliases, and future local
expiry, then submits the existing API payload with expiry converted to UTC.
Optional fields use an accessible disclosure; field errors open it and focus
the relevant input. Pending submissions are guarded and inputs remain read-only
until completion. RFC 7807 field validation, invalid URLs, alias conflicts, and
network/server failures produce helpful feedback without clearing fields.
Success displays a basic link; enhanced result interactions remain TICKET-016.
`GET /ui/config` exposes the existing configured public base URL for the alias
prefix; Vite proxies it for local development. No new dependencies or schema
changes. Updated README and API/architecture documentation.

**Verification:** `mvn verify` passes all 261 Java and 34 frontend tests, with
no failures or skips, including lint, formatting, and the production build.
HTTP integration verifies the configured public prefix. Headless Chrome against
the packaged application and isolated PostgreSQL verified generated links,
custom alias plus expiry, and duplicate-alias feedback with retained input and
field focus. Final responsive checks at 320, 390, 768, and 1440px found no
horizontal overflow with optional fields collapsed or expanded. The creation
card starts at 391px on a 320px-wide viewport. Temporary smoke-test services
were removed after verification.

**Depends on:** TICKET-014 and TICKET-006.

**Goal:** Build the primary landing page and short-link creation workflow using `POST /api/v1/urls`.

**Component(s):** `CreateUrlForm`
**Acceptance criteria**
- Present a concise hero section and a prominent creation card above the fold.
- The form contains original URL, optional custom alias, and optional expiry fields.
- Add clear helper text for the alias pattern (3–16 characters; letters, numbers, `_`, and `-`) and display the public base URL beside the alias input.
- Validate required fields and obvious format errors client-side while keeping the backend authoritative.
- Disable repeated submission while a request is pending and show an accessible progress state.
- Map RFC 7807 validation, invalid URL, and duplicate-alias responses to helpful field or form messages.
- Never clear valid user input after an API or network failure.
- The complete workflow must be usable at 320px width without horizontal scrolling.
  **Required tests**
- `CreateUrlForm_validMinimumInput_submitsExpectedPayload()`
- `CreateUrlForm_optionalAliasAndExpiry_submitsExpectedPayload()`
- `CreateUrlForm_invalidInput_blocksSubmissionAndShowsMessage()`
- `CreateUrlForm_duplicateAlias_showsAliasError()`
- `CreateUrlForm_serverUnavailable_preservesInputAndShowsRetryMessage()`

---

### TICKET-016 — Creation result, copy, share, and retry interactions
**Status:** Complete (verified 2026-09-11).

**Implementation:** Added `CreationResult` with a prominent short URL, confirmed
destination/expiry metadata, and generated/custom alias status. Clipboard copy
has a selected inline manual fallback for unavailable or denied access. Native
sharing is shown only when supported; failures/cancellation preserve the result.
Success and action feedback use a live region. Open-link and analytics actions
use new tabs; analytics currently targets the existing JSON stats endpoint until
TICKET-017 adds its page. Reset clears form/result/error state and focuses the
destination input. No link data is persisted and mounting never creates a link.
No backend, dependency, or schema changes.

**Verification:** `mvn verify` passes 261 Java and 46 frontend tests without
failures or skips, including lint, formatting, and the production build. Tests
cover metadata, clipboard success/unavailability/denial, sharing support,
cancellation/failure, reset focus and field clearing, malformed responses, and
remount without resubmission or browser storage. Chrome checks using production
assets and mocked API responses passed at 320, 390, 768, and 1440px with long
URLs and no horizontal overflow. Manual copy selected the complete link; reset
and refresh left the creation-request count unchanged.

**Depends on:** TICKET-015.

**Goal:** Turn a successful API response into a polished, useful result experience.

**Component(s):** `CreationResult`
**Acceptance criteria**
- Show the generated short URL in a high-visibility success panel without navigating away from the page.
- Provide copy-to-clipboard with an inline fallback when the Clipboard API is unavailable.
- Provide native sharing when Web Share is supported and hide the action when it is not supported.
- Display original URL, expiry status, and whether the code is generated or custom.
- Provide clear actions for "Open link," "View analytics," and "Shorten another."
- "Shorten another" resets transient state intentionally; browser refresh must not silently resubmit the previous request.
- Announce success and copy status through an ARIA live region.
  **Required tests**
- `CreationResult_success_displaysShortUrlAndMetadata()`
- `CreationResult_copySupported_copiesShortUrlAndConfirms()`
- `CreationResult_clipboardUnavailable_showsManualCopyFallback()`
- `CreationResult_shortenAnother_resetsFormAndResult()`

---

### TICKET-017 — Link analytics lookup and delete workflow
**Status:** Backlog.

**Depends on:** TICKET-014, TICKET-008, and TICKET-009.

**Goal:** Let a user inspect one known short code and safely delete it using the current v1 API. This is not an all-links dashboard because v1 has no list endpoint or ownership model.

**Component(s):** `AnalyticsLookup`
**Acceptance criteria**
- Add an analytics route with a short-code lookup form backed by `GET /api/v1/urls/{shortCode}`.
- Display original URL, short URL, click count, creation time, last-accessed time, expiry time, custom-alias status, and current expired/active state.
- Use properly formatted dates while preserving the exact timestamp in accessible text or a tooltip.
- Distinguish empty/unvisited, expired, not-found, network-error, loading, and success states.
- Add delete behind a confirmation modal that identifies the exact short code and explains that deletion is permanent.
- Call `DELETE /api/v1/urls/{shortCode}` only after explicit confirmation; on success, clear stale analytics and show confirmation.
- Do not store link data in browser storage.
  **Required tests**
- `AnalyticsLookup_existingCode_rendersStatsWithoutIncrementingClicks()`
- `AnalyticsLookup_unknownCode_rendersNotFoundState()`
- `AnalyticsLookup_expiredCode_rendersExpiredState()`
  **Delete tests**
- `DeleteLink_cancelled_doesNotCallApi()`
- `DeleteLink_confirmed_deletesAndClearsStats()`

---

### TICKET-018 — Responsive quality, accessibility, and UI hardening
**Status:** Backlog.

**Depends on:** TICKET-015 through TICKET-017.

**Goal:** Make the complete frontend production-quality across devices, assistive technologies, and unreliable networks.

**Component(s):** `ApiErrorBoundary` (plus cross-cutting layout, accessibility, and CSP work across all routes)
**Acceptance criteria**
- Verify layouts at 320px, 375px, 768px, 1024px, 1440px, and 1920px widths.
- Meet WCAG 2.2 AA for keyboard operation, landmarks, labels, focus order, contrast, errors, and status announcements.
- Add a skip link and ensure every modal traps focus, closes with Escape, and restores focus to its trigger.
- Respect `prefers-reduced-motion`; decorative animation must never block interaction.
- Prevent layout shift in loading and result states with stable containers or skeletons.
- Define friendly offline, timeout, malformed-response, 429, and 5xx states without exposing stack traces.
- Add a strict Content Security Policy compatible with the built assets; do not use inline scripts or render unsanitized API content as HTML.
- Achieve agreed Lighthouse CI minimums on the production build: Accessibility 95, Best Practices 95, SEO 90, and Performance 85.
  **Required tests**
- Automated accessibility scan reports no serious or critical violations on create and analytics routes.
- Keyboard-only end-to-end test completes create, copy, analytics lookup, and delete confirmation.
- Responsive end-to-end smoke tests pass at mobile and desktop viewports.
- `ApiErrorBoundary_unexpectedFailure_showsSafeRecoveryUi()`

---

### TICKET-019 — Frontend CI, end-to-end tests, and deployment documentation
**Status:** Backlog.

**Depends on:** TICKET-012 and TICKET-018.

**Goal:** Make frontend quality part of the normal build and release path.

**Acceptance criteria**
- CI runs frontend lint, type-check, unit/component tests, production build, and backend `mvn clean verify` on every PR.
- Add Playwright end-to-end coverage against the containerized app and PostgreSQL with isolated test data.
- Cache Maven and Node dependencies without caching secrets or generated runtime data.
- The Docker image contains only production frontend assets, not `node_modules`, source maps containing local paths, or development servers.
- Add a frontend architecture section and screenshots for mobile and desktop to `README.md`.
- Document configuration, local proxying, build troubleshooting, and the single-container production flow.
  **Required tests** (Playwright end-to-end, containerized app)
- `userCreatesShortUrlAndOpensRedirect()`
- `userLooksUpAnalyticsAndDeletesLink()`
- `duplicateAliasDisplaysActionableError()`
- CI/container smoke test proves `/`, `/analytics`, and `/api/v1/urls` are reachable through the expected application origin.

---

## Future / backlog (not required for v1)

---

### TICKET-F01 — API key authentication, ownership, and v2 boundary
**Status:** Backlog.

**Goal:** Authenticate API clients and enforce per-owner isolation for every management operation.

**Acceptance criteria**
- Store only a strong hash and non-secret prefix of each API key; show the full key once at creation.
- Associate each new v2 short URL with an owner; add the relationship through Flyway.
- Require an API key on `/api/v2/urls/**`; return 401 for missing/invalid credentials and 403 where an authenticated principal lacks permission.
- Create, stats, list, and delete operations may access only the authenticated owner's records.
- Define key creation, rotation, revocation, last-used timestamp, and audit behavior.
- Define a deterministic migration policy for pre-v2 links; do not guess ownership.
- Use constant-time comparison where applicable and never log raw API keys.
- Publish the v1 deprecation/sunset behavior and migration examples in OpenAPI and `docs/API_REQUESTS.md`.
  **Required tests**
- `authenticate_validApiKey_returnsOwnerPrincipal()`
- `authenticate_invalidOrRevokedApiKey_returns401()`
- `getStats_otherOwnersCode_doesNotDiscloseResource()`
- `delete_otherOwnersCode_doesNotDeleteResource()`
- `create_authenticatedOwner_persistsOwnership()`
- Repository integration tests verify ownership constraints and key-prefix uniqueness.

---

### TICKET-F02 — Redis redirect cache
**Status:** Backlog.

**Depends on:** TICKET-F01.

**Goal:** Reduce database reads on hot redirects without returning deleted, expired, or changed destinations.

**Acceptance criteria**
- Cache only the minimum redirect data with a TTL no longer than the link's remaining lifetime.
- Use cache-aside reads; Redis failure falls back to PostgreSQL and does not break redirects.
- Evict cache entries on delete, expiry-state change, or future destination update.
- Preserve correct click analytics; caching the destination must not lose or double-count clicks.
- Expose cache hit, miss, eviction, and failure metrics without high-cardinality labels.
  **Required tests**
- `resolve_cacheHit_returnsDestinationWithoutLookup()`
- `resolve_cacheMiss_loadsDatabaseAndCachesResult()`
- `resolve_expiringLink_capsCacheTtlAtExpiry()`
- `delete_existingLink_evictsCachedEntry()`
- Integration test verifies behavior while Redis is unavailable.

---

### TICKET-F03 — Owner-aware rate limiting and quotas
**Status:** Backlog.

**Depends on:** TICKET-F01 and TICKET-F02.

**Goal:** Protect create and redirect traffic with configurable, observable limits.

**Acceptance criteria**
- Rate-limit authenticated management calls primarily by owner/API key and public redirects by a privacy-reviewed client identifier.
- Configure limits through environment variables; do not hardcode production policy.
- Return `429` ProblemDetail with standard rate-limit and `Retry-After` headers.
- Use a Redis-backed distributed strategy so limits remain correct with multiple app instances.
- Fail according to a documented fail-open/fail-closed policy per endpoint class.
  **Required tests**
- `create_withinLimit_succeeds()`
- `create_limitExceeded_returns429WithRetryAfter()`
- `rateLimit_differentOwners_haveIndependentBuckets()`
- Multi-instance integration test verifies a shared distributed limit.

---

### TICKET-F04 — Bulk URL creation
**Status:** Backlog.

**Depends on:** TICKET-F01 and TICKET-F03.

**Goal:** Add `POST /api/v2/urls/bulk` for bounded batch creation.

**Acceptance criteria**
- Enforce a configurable maximum batch size and request-body size.
- Preserve input order and return per-item success or RFC 7807-compatible failure details.
- Define atomicity explicitly; recommended default is partial success with no silent rollback of valid items.
- Detect duplicate aliases both within the request and against persisted records.
- Apply ownership, quota, rate-limit, URL validation, alias, and expiry rules consistently with single creation.
- Prevent N+1 uniqueness lookups and unbounded memory use.
  **Required tests**
- `bulkCreate_allValid_returnsOrderedSuccesses()`
- `bulkCreate_mixedValidity_returnsPerItemResults()`
- `bulkCreate_duplicateAliasWithinBatch_reportsConflict()`
- `bulkCreate_exceedsMaximum_returns413Or400ProblemDetail()`
- Repository integration test verifies persisted ownership for successful items.

---

### TICKET-F05 — QR code generation
**Status:** Backlog.

**Depends on:** TICKET-F01.

**Goal:** Generate a QR representation of an owned short URL without persisting redundant image blobs.

**Acceptance criteria**
- Add owner-protected SVG and PNG endpoints with explicit content types and cache headers.
- Encode the public short URL, never the original destination.
- Bound size, margin, and error-correction inputs to safe documented values.
- SVG output must be generated by the application and contain no scripts or external references.
- The frontend offers preview and download actions with an accessible text alternative.
  **Required tests**
- `generateQr_existingOwnedCode_returnsScannableImage()`
- `generateQr_unknownOrForeignCode_doesNotDiscloseResource()`
- `generateQr_invalidOptions_returns400ProblemDetail()`
- Image decoding test confirms the QR resolves to the expected short URL.

---

### TICKET-F06 — Link-safety and malware scanning
**Status:** Backlog.

**Depends on:** TICKET-F01.

**Goal:** Evaluate submitted destinations against a pluggable safety provider before activation.

**Acceptance criteria**
- Introduce `PENDING`, `ACTIVE`, `REJECTED`, and `SCAN_FAILED` safety states via Flyway.
- Normalize and validate URLs before scanning; block unsupported schemes, embedded credentials, and configured private/internal address ranges.
- Define synchronous vs asynchronous activation and provider timeout/retry behavior in `ARCHITECTURE.md`.
- Reject known-malicious destinations with a safe ProblemDetail that does not expose provider internals.
- Cache scan verdicts for a bounded period and keep an auditable provider/verdict timestamp.
- Provider outage follows a documented policy and is observable.
  **Required tests**
- `create_safeDestination_activatesLink()`
- `create_maliciousDestination_rejectsLink()`
- `create_privateNetworkDestination_isRejectedBeforeProviderCall()`
- `create_scannerTimeout_appliesDocumentedFailurePolicy()`
- Integration test verifies pending links cannot redirect.

---

### TICKET-F07 — Paginated owner dashboard API
**Status:** Backlog.

**Depends on:** TICKET-F01.

**Goal:** Support a real authenticated link-management dashboard.

**Component(s):** Frontend analytics area (authenticated, paginated dashboard)
**Acceptance criteria**
- Add `GET /api/v2/urls` with bounded pagination, deterministic sorting, and filters for status, alias, creation range, and expiry.
- Return only the authenticated owner's links and expose pagination metadata.
- Avoid returning API key data or internal entity fields.
- Add indexes justified by query plans and introduced through Flyway.
- Update the frontend analytics area into an authenticated paginated dashboard with empty, loading, and filtered states.
  **Required tests**
- `listUrls_authenticatedOwner_returnsOnlyOwnedLinks()`
- `listUrls_filtersAndSortsDeterministically()`
- `listUrls_pageSizeAboveMaximum_isRejectedOrCapped()`
- Repository integration tests validate pagination and relevant indexes.

---

### TICKET-F08 — Time-series analytics and privacy controls
**Status:** Backlog.

**Depends on:** TICKET-F01.

**Goal:** Provide useful trends beyond a lifetime click counter while collecting the minimum necessary data.

**Acceptance criteria**
- Record aggregate click buckets by link and time period; define timezone behavior explicitly.
- Provide owner-protected trend endpoints with bounded date ranges and granularity.
- Define retention and anonymization for IP-derived, referrer, user-agent, country, or device attributes before collecting any of them.
- Keep redirects resilient if analytics persistence is degraded; document delivery guarantees.
- Add low-cardinality metrics for dropped/delayed analytics events.
- Display accessible charts plus equivalent tabular data in the frontend.
  **Required tests**
- `recordClick_sameBucket_incrementsAggregate()`
- `getTrend_foreignLink_doesNotDiscloseAnalytics()`
- `getTrend_invalidRange_returns400ProblemDetail()`
- Integration test verifies total and bucketed counts reconcile under concurrent clicks.

---

### TICKET-F09 — Link lifecycle management
**Status:** Backlog.

**Depends on:** TICKET-F01, TICKET-F02, and TICKET-F07.

**Goal:** Let owners update destinations/expiry and temporarily deactivate links with safe cache behavior.

**Acceptance criteria**
- Add owner-protected update and activate/deactivate operations with optimistic locking.
- A destination change requires the same validation and safety checks as creation.
- Short code and ownership are immutable.
- Deactivated links return a documented response and do not increment click analytics.
- Every state change evicts the redirect cache and records an audit event.
- The frontend warns clearly before changing a live destination.
  **Required tests**
- `updateDestination_ownedLink_updatesAndEvictsCache()`
- `updateShortCode_attempt_isRejected()`
- `deactivateLink_thenResolve_returnsDocumentedStatus()`
- `update_staleVersion_returns409ProblemDetail()`

---

### TICKET-F10 — Audit trail, operational readiness, and v1 retirement
**Status:** Backlog.

**Depends on:** TICKET-F01 through TICKET-F09, as applicable.

**Goal:** Make v2 supportable in production and complete the planned retirement of insecure v1 management operations.

**Acceptance criteria**
- Record immutable audit events for key creation/revocation, link create/update/delete, ownership migration, and administrative actions.
- Never store raw API keys, full sensitive headers, or unnecessary destination query secrets in logs/audit metadata.
- Add dashboards/alerts for redirect latency, error rate, database pool pressure, Redis failures, scan failures, rate-limit rejection, and analytics lag.
- Document backup/restore, key-compromise response, dependency outage behavior, and rollback procedures.
- Publish and enforce the v1 sunset date; retired v1 management endpoints return a documented retirement response or are removed in a major deployment.
- Keep existing unversioned short-link redirects working unless a link was explicitly deleted, expired, rejected, or deactivated.
  **Required tests**
- `sensitiveOperations_emitSanitizedAuditEvents()`
- `auditEvent_rawApiKeyOrAuthorizationHeader_isNeverStored()`
- `retiredV1ManagementEndpoint_returnsDocumentedResponse()`
- Restore drill and production smoke-test evidence are attached to the ticket/PR.