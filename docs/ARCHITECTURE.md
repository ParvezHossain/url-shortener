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

Redirect resolution uses a cache-aside Redis layer for hot codes. The cache stores
only the destination and expiry timestamp. A hit atomically increments analytics
in PostgreSQL without loading the entity; a miss uses the pessimistic row-lock
path above, then caches the result. Cache TTLs are capped at the remaining link
lifetime, permanent links use a configurable bounded TTL, and Redis failures fall
back to PostgreSQL. Deletes and expired resolutions evict entries. Cache metrics
use fixed operation names (`hits`, `misses`, `evictions`, and `failures`) without
short-code labels.
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
| `config` | `OpenApiConfig`, `CacheConfig`, `RateLimitConfig` | — |
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
- **Observability**: Spring Boot Actuator (`/actuator/health`, `/actuator/metrics`), built-in ECS JSON console logging in the `docker` profile, with the startup banner disabled. Other profiles retain default text logging. Creation, resolution, and deletion events log short codes at INFO without destination URLs; these are service execution logs, not a durable audit trail. The endpoint allowlist is `health,info,metrics`; no additional logging or metrics dependency is needed.
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
- Distributed rate limiting is implemented in TICKET-F03; see section 14.
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

## 12. Frontend release path (TICKET-019)

PR verification uses Maven to run both toolchains, then tests the Compose image
with isolated PostgreSQL data. Release E2E tests cover creation and a real
redirect, analytics lookup and deletion, duplicate-alias recovery, and same-origin
UI/API reachability. Analytics retains hash routing so a short alias named
`analytics` is not intercepted. CI audits the final image for production-only
assets before browser and Lighthouse checks. See `FRONTEND_DEPLOYMENT.md` for
configuration, proxying, build troubleshooting, and reproduction commands. The
Docker datasource honors `POSTGRES_URL`, including Compose's custom database name.


## 13. API keys and ownership (TICKET-F01)

`security/ApiKeyFilter` authenticates the entire `/api/v2` boundary before routing
and passes an immutable `OwnerPrincipal` request attribute. Controllers never accept
an owner from request JSON. `ApiKeyService` owns authentication and key lifecycle;
`ApiKeyRepository` uses the existing JDBC dependency for transactional key/audit SQL.
No new dependency is introduced. Keys contain a 96-bit random non-secret prefix
and a 256-bit SecureRandom secret. Only SHA-256 of the entire random credential is
stored, with constant-time hash comparison. This is a machine-generated token,
not a human password. Malformed/unknown/revoked keys produce the same 401 detail.
Authentication locks the key row, commits last-used time, and then releases it;
requests already authenticated before revocation can finish. Concurrent rotation
has one winner; revocation and issuance/audit share one transaction.

Flyway V2 in the active PostgreSQL migration directory adds `api_owner`, `api_key`,
`api_audit`, and nullable `short_url.owner_id` with foreign keys. Prefix is the key
primary key. The owner/creation/ID index supports deterministic bounded listing;
key owner index supports operator recovery queries. Existing rows remain NULL.
New v2 links require an owner in the service and are associated on their initial
insert. v1 remains anonymous but its stats/delete paths explicitly require NULL
ownership. Public resolve deliberately ignores ownership. Owner mismatch returns
404 without resource metadata; key lifecycle permission failures return 403.

The operator-only provisioning script accepts PostgreSQL connection environment
settings, generates credentials locally, sends only a hash to psql over stdin,
and prints the full key once after successful commit. It never writes a key file.
Audit rows contain key lifecycle metadata only; application code appends events
but does not expose audit mutation endpoints. Database administrators can still
modify rows; tamper-resistant audit infrastructure is F10. V1 sunset is optional
and configured through `APP_V1_SUNSET`; see API_REQUESTS.md for the transition contract.


The pre-existing PostgreSQL V1 variant diverged from the original timestamp/default
contract. V3 restores TIMESTAMPTZ, default creation time, default alias flag, and
the expected unique-constraint name without editing V1. Existing timestamp values
are interpreted as UTC, matching Hibernate's Instant writes. A populated-database
migration test verifies preservation of legacy destinations, codes, clicks, expiry,
and NULL ownership. MySQL is not a supported runtime here (no driver/Flyway module
in pom.xml); these migrations target the configured PostgreSQL location only.

## 14. Distributed request quotas (TICKET-F03)

`RateLimitInterceptor` runs after API-key authentication and MVC routing, before
URL/key management or redirect controller execution. All mapped v2 management
calls share a bucket by authenticated owner UUID, regardless of API-key prefix;
rotation and multiple keys cannot multiply the owner's quota. Legacy v1 calls
share a separate anonymous management bucket by client address. GET and HEAD
redirects share a client bucket across short codes. Failed downstream requests
consume quota; rejected requests do not extend the window. OPTIONS, frontend,
static assets, health, and API documentation are excluded. Unknown v2 routes
still require authentication, but do not consume quota.

`RateLimitService` uses a single Redis Lua script to atomically admit and increment
requests and set expiry on the first request. A fixed window starts at that first
request, using Redis TTL rather than application clocks. Denied requests neither
increment nor extend it. Two instances share the same quota, and no local fallback
counter can multiply it. Fixed windows allow bursts across a window boundary.
Redis key eviction, restart without persistence, or changing policy resets quotas;
use a dedicated, capacity-managed Redis deployment with persistence/noeviction
when quota continuity across Redis restarts is required. This is a request quota,
not a billing or stored-link quota. No database changes or dependencies are added.

Privacy review: bucket suffixes are HMAC-SHA256 of a namespaced owner UUID or the
socket peer address using a shared secret of at least 32 bytes. Redis never receives
raw IP addresses, API keys, destinations, or owner IDs. Keys expire after the
configured window; no identities appear in logs or metric labels. Digests are
pseudonymous, not anonymous, and remain correlatable while the secret is unchanged.
Forwarded/X-Forwarded-For headers are ignored. Keep server forwarded-header handling
disabled (the default); behind a proxy, clients share the proxy's quota. Supporting
trusted proxy client extraction requires an explicit deployment trust policy.
Rotate the shared secret across all instances together; rotation resets buckets.

Management fails closed with 503 and Retry-After: 1 when Redis cannot decide.
Redirects fail open on Redis errors to preserve existing link availability.
Store failures increment `rate.limit.requests{category, outcome="unavailable"}`;
normal outcomes are `allowed` and `denied`. Categories are fixed `management` and
`redirect`; no identity labels. Monitor failures because redirects are unprotected
during Redis outages. Redis connection/command timeouts default to one second and can be tuned through
`REDIS_CONNECT_TIMEOUT` / `REDIS_COMMAND_TIMEOUT`.

Enable using `RATE_LIMIT_ENABLED=true`, `RATE_LIMIT_SECRET`,
`RATE_LIMIT_MANAGEMENT`, `RATE_LIMIT_REDIRECT`, and `RATE_LIMIT_WINDOW_SECONDS`.
Limits/window must be positive integers; missing/invalid enabled configuration
fails startup. All instances must use identical configuration and Redis. Defaults
leave limiting disabled to avoid inventing production policy or a shared secret.
Compose passes all five variables through; set them before exposing the service.

## 15. QR generation (TICKET-F05)

`QrCodeController` exposes the two v2 image routes and validates `QrOptionsRequest`.
`QrCodeService` reuses `UrlShortenerService.getStats(code, owner)` for ownership
and canonical public URL construction. It never calls `resolve`, fetches a
remote destination, modifies analytics, or persists image data. Unknown and
foreign links retain the existing indistinguishable 404 behavior. Expired owned
links are handled like metadata reads. The authentication filter and F03 management
quota also cover QR routes. Images use no-store rather than a cache whose entries
could outlive permission changes.

New dependency: `com.google.zxing:core:3.5.4` provides standards-compliant QR
encoding and decoding for tests. No ZXing JavaSE or frontend QR dependency is
needed. JDK ImageIO writes PNG; SVG is a fixed application-owned template with
numeric rectangles and fixed text only. No destination, URL, credential, script,
external image reference, or user-supplied XML enters the SVG. Geometry is scaled
by whole pixels, centered, and bounded to 1024 square pixels; a minimum two pixels
per module avoids undersized output. Margins remain at least four modules. Both
rendered formats are decoded in tests to the expected public URL. No migrations.

`QrCodePanel` is available at `/#/qr` via the main navigation. It accepts an
operator-provisioned API key in a password field held only in component memory.
It fetches owned metadata and both images, then provides PNG preview, accessible
URL text, and download links. Inputs changing clear the old preview; clearing or
unmounting revokes object URLs. Pending work is cancelled on unmount and duplicate
submissions are blocked. This is a focused owned-link workflow; F04 remains backlog.
The shared request transport now buffers bytes rather than decoding everything
as text so binary image bytes survive its existing timeout/cancellation boundary.
Frontend CSP permits `blob:` only for images to support authenticated previews;
script, object, connection, and frame restrictions are unchanged. SVG downloads
are never injected into the document as markup.
