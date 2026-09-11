# Architecture

## 1. Style
Classic **layered architecture** (controller → service → repository → database), package-by-layer, single deployable Spring Boot application. Chosen over hexagonal/clean architecture deliberately: this project's scope doesn't justify the extra indirection, and package-by-layer is easier for an agent or new contributor to navigate predictably (see AGENTS.md rule: "no business logic in controllers").

## 2. High-level component diagram (textual)
```
Client
  │  HTTP (JSON)
  ▼
[UrlController]  ── validates request shape (@Valid), maps DTO <-> domain call
  │
  ▼
[UrlShortenerService]  ── business rules: alias validation, expiry, collision handling
  │           uses → [ShortCodeGenerator] (Base62 util, stateless)
  ▼
[ShortUrlRepository]  ── Spring Data JPA
  │
  ▼
PostgreSQL  ── table: short_url (see migration V1)

Redirect path:
Client → GET /{code} → [RedirectController] → UrlShortenerService.resolve(code)
       → 302 Location: originalUrl  (after transactional click increment)
```

## 3. Packages and responsibilities
| Package | Responsibility | Depends on |
|---|---|---|
| `controller` | HTTP layer: request mapping, `@Valid` input, DTO mapping, status codes | `service`, `dto` |
| `service` | Business logic: interface (`UrlShortenerService`) + impl (`UrlShortenerServiceImpl`) | `repository`, `domain`, `exception`, `util` |
| `repository` | `ShortUrlRepository extends JpaRepository<ShortUrl, Long>` + custom finder methods | `domain` |
| `domain` | JPA entities (`ShortUrl`) | — |
| `dto` | Request/response records, never shared with `domain` | — |
| `exception` | Domain exceptions + `GlobalExceptionHandler` (`@RestControllerAdvice`) | — |
| `config` | `OpenApiConfig`, `CacheConfig`, `RateLimitConfig` (future) | — |
| `util` | `Base62Encoder`, stateless helpers | — |

Dependency direction is strictly top-to-bottom in the table; `domain`/`util`/`exception` have no outward dependencies.

## 4. Data model
**Table: `short_url`**
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL PK` | internal id, also seeds Base62 code for the default (non-custom-alias) path |
| `short_code` | `VARCHAR(16) UNIQUE NOT NULL` | Base62 code or custom alias |
| `original_url` | `TEXT NOT NULL` | validated absolute URL |
| `custom_alias` | `BOOLEAN NOT NULL DEFAULT FALSE` | true if user supplied the code |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `expires_at` | `TIMESTAMPTZ NULL` | nullable = never expires |
| `click_count` | `BIGINT NOT NULL DEFAULT 0` | incremented on each successful redirect |
| `last_accessed_at` | `TIMESTAMPTZ NULL` | |

Index: unique index on `short_code` (lookup path is always by code).

## 5. Short code generation strategy
- Default: Base62 encoding (`[0-9A-Za-z]`) of the auto-incremented `id`, left-padded/shuffled to avoid sequential guessability being *too* obvious for a portfolio project — documented trade-off, not a security control (see Non-goals in PROJECT_OVERVIEW.md; this is not designed to resist enumeration attacks in v1).
- Creation currently inserts a reserved temporary code (`~` plus 15 random hex digits),
  obtains the PostgreSQL identity, and updates the code to its Base62 value in one
  transaction. The repository update clears the persistence context; the service
  returns a response DTO with the final code. No entity setter or schema change is
  needed. A failed insert/update rolls back creation.
- Custom alias: preserved as supplied (case-sensitive), validated at both the DTO
  boundary and service against `^[a-zA-Z0-9_-]{3,16}$`, uniqueness enforced by the DB unique constraint; a `DuplicateAliasException` (409) is thrown on conflict. The service checks existing
  codes, then inserts and flushes with `custom_alias=true`; a violation of
  `uq_short_url_short_code` is also translated to 409 to handle concurrent claims.
  Invalid aliases raise `InvalidUrlException` (400) for direct service callers.

Creation validates optional expiry at both the DTO and service boundaries: a
non-null timestamp must be strictly in the future. Both generated and custom links
persist it in the existing `expires_at` column and return it in the response;
`null` means no expiry. Invalid service input raises `InvalidUrlException` with
`expiresAt must be in the future`. Resolution is implemented in TICKET-007: the service looks up the code with a
pessimistic write lock, rejects unknown/expired links, and calls `recordAccess()`
on the managed entity. JPA commits the analytics before the controller returns
302. The row lock prevents lost updates under concurrent redirects. This uses
synchronous updates to satisfy the ticket's persisted analytics requirements.
The controller returns `Cache-Control: no-store` to keep subsequent accesses
passing through expiry checks and analytics.

Statistics use `GET /api/v1/urls/{shortCode}` and a read-only service transaction.
`getStats` uses the ordinary repository lookup and maps the entity to
`ShortUrlStatsResponse`; it does not call `resolve`, acquire a write lock, or
record an access. Expired links remain available for statistics; unknown codes
raise `UrlNotFoundException` (404).

Deletion uses `DELETE /api/v1/urls/{shortCode}` and a transactional service method.
It acquires the same row lock as resolution, then removes the entity and its
analytics. The controller returns 204 after commit. Unknown codes and repeated
deletions return 404; expired links can also be deleted. Concurrent deletes of
one existing link produce one success and one not-found response.

## 6. Error handling
`GlobalExceptionHandler` (`@RestControllerAdvice`) maps:
| Exception | HTTP status |
|---|---|
| `InvalidUrlException` | 400 |
| `DuplicateAliasException` | 409 |
| `UrlNotFoundException` | 404 |
| `UrlExpiredException` | 410 |
| `MethodArgumentNotValidException` (bean validation) | 400 |
| anything else | 500, generic `ProblemDetail`, no stack trace leaked |

All error responses use RFC 7807 `ProblemDetail` shape (`type`, `title`, `status`, `detail`, `instance`).
The HTTP content type is `application/problem+json`; `instance` identifies the
request path. Bean validation adds field messages in an `errors` array without
rejected values. Missing/malformed JSON returns a generic 400 message without
parser details. Unexpected failures return a generic 500 message without exception
messages, causes, or stack traces. `GlobalExceptionHandlerTest` verifies these
contracts through MVC, including every domain exception.

## 7. Cross-cutting concerns
- **Migrations**: Flyway, versioned SQL under `src/main/resources/db/migration`. No `ddl-auto=update` in any profile that touches a shared DB.
- **Config**: `application.yml` with Spring profiles `local`, `docker`, `test`; secrets via env vars (`POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
- **Observability**: Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`), structured JSON logging in the `docker`/prod profile.
- **API docs**: springdoc-openapi, exposed at `/swagger-ui.html` and `/v3/api-docs`.
  `OpenApiConfig` supplies API metadata and reusable problem response components.
  Controllers declare operation summaries and applicable response codes; request
  DTO annotations describe validation and optional fields. Generic advice-response
  inference is disabled so endpoints only advertise their applicable domain errors.

## 8. Deployment shape
`docker-compose.yml` with two services: `app` (built from `Dockerfile`, multi-stage: Maven build → slim JRE runtime) and `postgres`. App waits for Postgres healthcheck before starting; Flyway migrates on boot.
The runtime image uses a non-root user and an HTTP health check. Compose `--wait`
waits for the database and app to become healthy. GitHub Actions runs clean Maven
verification on each PR and main-branch push, then builds and health-checks the
Compose stack. Any failing verification or container startup fails the job.

## 9. Future extension points (see TICKETS.md "Future" section)
- Auth (API keys or OAuth2) → would add a `user_id` FK to `short_url` and a `security` package.
- Redis cache in front of `resolve()` for hot codes.
- Rate limiting via Bucket4j at the controller/filter layer.
- Horizontal scaling: move ID generation off the Postgres sequence to Snowflake-style IDs if multiple write nodes are ever needed.

## 10. Frontend foundation (TICKET-014)

`frontend/` contains React + TypeScript, built with Vite. `AppShell` owns the
responsive header, anchor navigation, main landmark, and footer. Shared UI
primitives and `ThemeToggle` use application-owned CSS custom properties for
light/dark color palettes, typography, spacing, radii, elevation, focus and motion.
No UI or routing library is introduced. The landing page hosts `CreateUrlForm`, which posts to the existing creation
API and maps ProblemDetail feedback to fields. `CreationResult` provides copy/share and reset actions; `AnalyticsLookup`
handles single-link analytics and confirmed deletion.

Maven invokes npm using `exec-maven-plugin` during resource generation and tests,
then copies the Vite output to `classpath:/static`. A thin `FrontendController` serves `/` directly from the packaged HTML; using
the default welcome-page forward would collide with `/{shortCode}` at
`/index.html`. Spring Boot serves `/assets/*` through static resource handling. There is
no catch-all SPA forwarding: `/{shortCode}` continues to resolve links and retain
its existing error semantics. Docker builds assets in a Node 24 stage before
packaging the single executable Spring Boot jar. No runtime frontend service or
production CORS configuration is required. Development-only proxy configuration
lives in `vite.config.ts`.

`GET /ui/config` returns a `FrontendConfigResponse` containing the configured
public base URL. This keeps the alias preview consistent with service-generated
links across deployments without rebuilding JavaScript. `CreateUrlForm` owns
input, validation, pending, error, and confirmed result state. It guards duplicate
submissions, preserves fields on failure, and converts optional local expiry to
UTC. Runtime configuration and creation requests use relative same-origin paths;
Vite proxies `/ui` as well as the API during development.

`CreationResult` consumes validated creation metadata and the submitted alias
choice (the creation response has no alias-type flag). Copy/share status lives
inside the result component and is discarded when the parent resets it. Results
and form fields never enter browser storage. Link-opening actions use new tabs
with `noopener noreferrer`; analytics targets `/#/analytics?code=...`, which loads the side-effect-free
`GET /api/v1/urls/{shortCode}` endpoint. Clipboard
and Web Share failures are handled locally without repeating the creation POST.

`AppShell` observes hash changes using `useSyncExternalStore`. `/#/analytics`
accepts an optional `code` query within the fragment, without introducing server
paths that could shadow short codes. Pending lookups are aborted on unmount and
late responses ignored. New lookups clear old results. Delete confirmation
captures the exact result code and blocks duplicate submission. HTTP 204 clears
the result; 404 clears stale details with an already-absent message. Failed
deletes allow explicit retry. Stats now include the configured public URL and
persisted custom-alias flag, without a schema change or guessed classification.


## 11. Frontend security and quality (TICKET-018)

The HTML response receives a fresh script nonce and strict CSP. External theme
and application scripts carry that nonce; assets remain static. The frontend
uses no HTML injection. The policy restricts styles, images, fonts, and API
connections to the same origin and blocks framing, objects, and base changes.
Swagger responses retain their separate behavior. HTTP integration tests verify
nonce uniqueness, external-only scripts, and asset serving under the policy.

`apiRequest` is the shared transport boundary: it buffers responses within a
15-second deadline, supports cancellation, and classifies offline, timeout,
malformed, rate-limit, server, and network failures. It never retries writes.
`ApiErrorBoundary` catches rendering failures and offers a fresh page load.
Workflow slots reserve result space; the native dialog has explicit focus
wrapping/restoration and supports Escape even after confirmation (dismissal
never cancels an already submitted request). Quality tooling and thresholds are
specified in `FRONTEND_QUALITY.md`.
