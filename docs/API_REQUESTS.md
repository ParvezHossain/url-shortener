# API Requests

Base URL (local): `http://localhost:8080`
All bodies are JSON. All error responses follow RFC 7807 `ProblemDetail`.

---

## 1. Create a short URL
`POST /api/v1/urls`

**Request**
```json
{
  "originalUrl": "https://example.com/some/very/long/path?query=1",
  "customAlias": "my-link",       // optional, 3-16 chars [a-zA-Z0-9_-]
  "expiresAt": "2026-12-31T23:59:59Z"  // optional, ISO-8601, must be in the future
}
```

**Response `201 Created`**
```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://example.com/some/very/long/path?query=1",
  "createdAt": "2026-09-10T10:15:00Z",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

**Errors**
- `400` — `originalUrl` missing/malformed, or `customAlias` fails the pattern, or `expiresAt` is in the past.
- `409` — `customAlias` already taken.

---

## 2. Redirect
`GET /{shortCode}`

**Response**
- `302 Found`, `Location: <originalUrl>` header, empty body.

**Errors**
- `404` — unknown short code.
- `410` — short code exists but is past `expiresAt`.

---

## 3. Get stats for a short URL
`GET /api/v1/urls/{shortCode}`

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

**Response**
- `204 No Content`

**Errors**
- `404` — unknown short code.

---

## 5. Health & docs (operational, not business endpoints)
- `GET /actuator/health` — liveness/readiness.
- `GET /swagger-ui.html` — interactive API docs (springdoc-openapi).
- `GET /v3/api-docs` — raw OpenAPI JSON.

---

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
