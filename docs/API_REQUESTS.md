# API Requests

Base URL (local): `http://localhost:8080`
All bodies are JSON. All error responses follow RFC 7807 `ProblemDetail`.

---

## 1. Create a short URL
`POST /api/v1/urls`

**Implemented scope (TICKET-004/005/006):** generated codes or custom aliases for absolute HTTP/HTTPS URLs
with a host, up to 2048 characters by default (`app.short-code.max-original-url-length`).

**Request**
```json
{
  "originalUrl": "https://example.com/some/very/long/path?query=1"
}
```

**Response `201 Created`**, with `Location: http://localhost:8080/10`:
```json
{
  "shortCode": "10",
  "shortUrl": "http://localhost:8080/10",
  "originalUrl": "https://example.com/some/very/long/path?query=1",
  "createdAt": "2026-09-10T10:15:00Z",
  "expiresAt": null
}
```
The code is the Base62 encoding of the generated database ID; the example assumes
ID 62. The public origin comes from `APP_BASE_URL`. Creation commits the generated
code atomically before returning; the internal temporary code is never returned.

To choose a code, include `"customAlias": "My_link-1"` in the request. The response
uses that exact alias in `shortCode`, `shortUrl`, and `Location`. Aliases are
case-sensitive, must match `^[a-zA-Z0-9_-]{3,16}$`, and cannot reuse any existing
short code (generated or custom). Omit the field or use `null` to generate a code;
an empty or whitespace-only alias is invalid.

```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/path","customAlias":"My_link-1"}'
```

To set expiration, include an ISO-8601 timestamp with a timezone, for example
`"expiresAt": "2030-12-31T23:59:59Z"` (choose a date in the future). The timestamp
must be strictly later than the server's current time when validated. This works
with generated codes and custom aliases; the response includes the saved expiry.
Omitting `expiresAt` or supplying `null` creates a link with no expiry. Expired links are rejected during redirect without recording an access.

**Errors**
- `400` — missing, blank, malformed, non-HTTP(S), or over-length destination;
  missing/malformed JSON body.
- `400` — invalid alias pattern, malformed expiry timestamp, or expiry that is not in the future.
- `409` — the requested alias is already taken, including concurrent claims.

Creation, redirect, stats, and deletion are implemented.

---

## 2. Redirect
`GET /{shortCode}`

**Implemented (TICKET-007):** resolves generated codes and custom aliases, checks
expiry, and commits one click plus the last-access timestamp before redirecting.
Concurrent accesses to the same link are serialized to preserve every click.

**Response**
- `302 Found`, `Location: <originalUrl>` header, empty body.
- `Cache-Control: no-store` prevents cached redirects from bypassing expiry and analytics.

**Errors**
- `404` — unknown short code.
- `410` — short code exists but is past `expiresAt`; analytics remain unchanged.

---

## 3. Get stats for a short URL
`GET /api/v1/urls/{shortCode}`

**Implemented (TICKET-008):** returns metadata and recorded analytics for any
existing link, including expired links. Reading stats does not increment clicks
or change `lastAccessedAt`. A never-visited link has `clickCount: 0` and
`lastAccessedAt: null`; `expiresAt: null` means no expiry.
TICKET-017 adds `shortUrl` (using `APP_BASE_URL`) and `customAlias` (the persisted
alias flag). Existing fields and read-only semantics remain unchanged.
The frontend at `/#/analytics?code=my-link` uses this endpoint and sends deletion
only after confirmation.

**Response `200 OK`**
```json
{
  "shortCode": "my-link",
  "originalUrl": "https://example.com/some/very/long/path?query=1",
  "createdAt": "2026-09-10T10:15:00Z",
  "expiresAt": "2026-12-31T23:59:59Z",
  "clickCount": 42,
  "lastAccessedAt": "2026-09-10T12:00:00Z",
  "shortUrl": "http://localhost:8080/my-link",
  "customAlias": true
}
```

**Errors**
- `404` — unknown short code.

---

## 4. Delete a short URL
`DELETE /api/v1/urls/{shortCode}`

**Implemented (TICKET-009):** permanently removes the matching link and its
analytics, including expired links. After deletion, redirect and stats requests
return 404. Repeating deletion returns 404, not 204.

```bash
curl -i -X DELETE http://localhost:8080/api/v1/urls/my-link
```

**Response**
- `204 No Content`

**Errors**
- `404` — unknown short code.

---

## 5. Health & docs (operational, not business endpoints)
- `GET /actuator/health` — liveness/readiness.
- `GET /swagger-ui.html` — redirects to the interactive Swagger UI (springdoc-openapi).
- `GET /v3/api-docs` — generated OpenAPI JSON for creation, redirect, stats, and deletion,
  including operation summaries, request schemas, response codes, headers, and
  shared `application/problem+json` error schemas (TICKET-011).

---

## Error response contract

Domain and validation errors use `application/problem+json` with `type`, `title`,
`status`, `detail`, and `instance` (the request path). Field validation errors
also include an `errors` array, for example `"originalUrl: must not be blank"`.
Rejected input values are not included in that array.

Missing or malformed JSON returns 400 with `Request body is missing or malformed`.
Unexpected exceptions return 500 with `An unexpected error occurred`; exception
messages, causes, and stack traces are not exposed.

## Example error response shape
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No short URL found for code 'abc123'",
  "instance": "/api/v1/urls/abc123"
}
```

## Frontend entry point

`GET /` serves the frontend HTML (`text/html`, `Cache-Control: no-cache`). Its
versioned JavaScript and CSS are served from `/assets/`. This is separate from
the JSON API below `/api/v1`; short-code redirects keep their existing behavior.
During local frontend development, Vite proxies API and Swagger requests to the
backend (see README).

### Public frontend configuration

`GET /ui/config` returns `200 application/json`, for example:

```json
{"publicBaseUrl":"https://links.example.com"}
```

The value comes from `APP_BASE_URL`; the creation form uses it beside the alias
input. This public presentation endpoint is excluded from the URL API's OpenAPI
operations. The create form posts to the existing `POST /api/v1/urls` endpoint,
omitting empty optional fields and converting local expiry to an ISO UTC timestamp.

## Operational metrics (TICKET-013)

`GET /actuator/metrics` lists available metric names.
`GET /actuator/metrics/jvm.memory.used` returns the metric name, measurements,
base unit, and available tags. `GET /actuator/metrics/http.server.requests`
reports HTTP request measurements after traffic is handled. These operational
endpoints use Actuator JSON rather than the `/api/v1` DTOs.

```bash
curl --fail http://localhost:8080/actuator/metrics
curl --fail http://localhost:8080/actuator/metrics/jvm.memory.used
```


## TICKET-F01: authenticated v2 management

Use HTTPS in production and send credentials only in `X-API-Key`, never in URLs
([OWASP REST guidance](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)).
The operator runs `python3 scripts/provision-api-key.py` using standard PostgreSQL
`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD`/`.pgpass` connection settings.
Run it after Flyway has applied V2. It creates an owner and key atomically and prints
the full credential once. Save it securely; there is no retrieval endpoint.
`--owner <UUID>` issues a recovery/additional key for an existing owner only.
The operator is responsible for verifying the recipient's identity. No public
registration, owner claiming, or administrative HTTP API is exposed.

```bash
# Read the operator-issued key without putting its literal value in shell history.
read -rsp 'API key: ' API_KEY
export API_KEY

# Create a URL.

curl -i http://localhost:8080/api/v2/urls \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/path","customAlias":"owned-demo"}'

# List the owner's URLs.  
  
curl -i -H "X-API-Key: $API_KEY" 'http://localhost:8080/api/v2/urls?page=0&size=20'

# Get statistics for one of the owner's URLs.

curl -i -H "X-API-Key: $API_KEY" http://localhost:8080/api/v2/urls/owned-demo

# Delete one of the owner's URLs.

curl -i -X DELETE -H "X-API-Key: $API_KEY" http://localhost:8080/api/v2/urls/owned-demo
```

Creation returns 201 and the existing creation DTO. Stats return 200 and the existing
stats DTO. Delete returns 204. Listing returns `{content, page, size, totalElements}`;
page is zero-based, size defaults to 20 and must be 1–100. Ordering is creation time
then ID descending. Foreign, legacy, and unknown codes return the same 404 response
from v2. Missing, malformed, duplicate, invalid, and revoked credentials return 401
ProblemDetail. Key lifecycle operations on any key other than the caller's current
key return 403. Every v2 response is `Cache-Control: no-store`.

The key prefix is the 24 hex characters after `usk_`. Rotate the current key:

```bash
curl -i -X POST -H "X-API-Key: $API_KEY" \
  http://localhost:8080/api/v2/keys/PREFIX/rotate
# Response: {"apiKey":"<new full key shown once>","prefix":"<new prefix>"}
# Replace your stored credential immediately; the old key is revoked atomically.
```

`DELETE /api/v2/keys/PREFIX` with the current key returns 204 and revokes it.
Keys do not expire automatically; they remain active until explicitly revoked.
Rotation/revocation have no grace period; already authenticated requests may finish.
A lost rotation response requires operator recovery, not replaying rotation with the
old key. Revoke all compromised keys explicitly; issuing a recovery key does not
silently revoke other keys. Authentication updates `last_used_at` even when the
subsequent management operation fails. Failed authentication does not update it.
Creation, rotation, and revocation append transactional `api_audit` events with
owner UUID, event type, timestamp, and non-secret prefixes only. Raw keys and headers
are never logged or persisted. Failed authentication is not individually audited;
rate limiting and broader link/admin audit trails remain F03/F10 work.

### v1 migration and sunset

V1 is deprecated from 2026-09-17 (`Deprecation` response header and OpenAPI flags).
Existing links remain unowned; v1 continues creating/managing only unowned links.
V1 can never read or delete v2-owned links, even if an API key is provided.
No owner is inferred from link codes or previous anonymous usage. To migrate,
obtain a key and recreate each desired destination through v2 with a new code.
Existing codes and public `GET /{code}` redirects remain intact. There is no automatic
claim/transfer endpoint or destructive backfill. The existing frontend stays on v1;
owner dashboard integration is tracked by F07.

Operators may set `APP_V1_SUNSET` to an ISO-8601 UTC timestamp and restart the app.
Before the deadline, v1 emits a `Sunset` header; from that instant all v1 URL
management requests return 410 ProblemDetail. Empty (default) means no scheduled
retirement. Publish the chosen deadline to clients before enabling it. Retirement
also disables the current frontend's v1 management workflows; redirects stay public.


### Swagger through the development proxy

Open `http://localhost:5173/swagger-ui/index.html` while Vite and the backend are
running. OpenAPI advertises the relative server `/`, so Try it out calls the same
browser origin; Vite forwards `/api`, `/swagger-ui`, and `/v3/api-docs` to the backend.
This avoids a cross-origin request from 5173 to 8080. Use Swagger's **Authorize**
button to supply the full API key. Restart the backend and reload Swagger after
changing OpenAPI configuration. Swagger directly on port 8080 works the same way.

A standalone `CorsConfigurationSource` bean does not itself install a CORS filter.
Skipping authentication for OPTIONS alone also does not produce CORS response
headers. The development proxy workflow does not require cross-origin API access.
