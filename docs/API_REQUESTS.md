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

**Response `200 OK`**
```json
{
  "shortCode": "my-link",
  "originalUrl": "https://example.com/some/very/long/path?query=1",
  "createdAt": "2026-09-10T10:15:00Z",
  "expiresAt": "2026-12-31T23:59:59Z",
  "clickCount": 42,
  "lastAccessedAt": "2026-09-10T12:00:00Z"
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
