# Frontend build and deployment

## One application origin

React and TypeScript compile to static assets with Vite. Maven copies `dist/`
into the Spring Boot jar. The final image serves HTML, assets, public UI config,
REST API, redirects, and Actuator from port 8080. PostgreSQL remains a separate
service; there is no frontend runtime container, Node process, or Vite server in
production. Deploy at the origin root; mounting under a URL prefix is unsupported.

The creation route is `/`. Analytics uses `/#/analytics` (optionally
`?code=...` inside the fragment). TICKET-019 originally called this `/analytics`;
the smoke test follows the existing hash route because `/analytics` belongs to
the short-code namespace and may already be a user's alias. A catch-all SPA
fallback would also break unknown-code 404 responses. The server receives `/`
for every frontend route. Owned links use `/#/links?mode=v2`; authenticated
analytics includes `mode=v2` in the fragment. Reloading requires entering the
API key again because credentials stay in memory. `/api/v1/urls` accepts POST, not collection GET.

## Configuration and local proxy

| Setting | Purpose |
| --- | --- |
| `APP_BASE_URL` | Public origin used in returned short links and `/ui/config`; use the externally reachable HTTPS origin behind a proxy. |
| `APP_PORT` | Compose host port, default 8080; match it in the local public base URL. |
| `POSTGRES_DB` | Compose database name, default `urlshortener`. |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database credentials supplied at runtime. |
| `POSTGRES_PORT` | Host database port, default 5432. |
| `POSTGRES_URL` | JDBC URL for host-run Spring Boot; Compose supplies its internal hostname and configured database name. |
| `API_PROXY_TARGET` | Vite-only backend target, default `http://localhost:8080`; never embedded in production assets. |
| `E2E_BASE_URL` | Running test application origin for browser checks and screenshots. |
| `CHROME_PATH` | Optional Chrome/Chromium executable override for browser and Lighthouse checks. |

Copy `.env.example` to `.env`, set credentials, the public origin, and a trusted
`SAFETY_SCANNER_ENDPOINT` (plus its token when required), then:

```bash
docker compose up --build --wait --wait-timeout 180
curl --fail http://localhost:8080/actuator/health
```

The Node stage checks and builds the UI; the Maven stage packages Java and static
assets; the non-root JRE stage contains the jar only. Flyway applies migrations
before health becomes ready. Put an HTTPS reverse proxy in front of port 8080,
forward requests to the same app, and preserve the application's CSP headers.
No CORS configuration or frontend rebuild is required when the public origin
changes. Keep PostgreSQL and its persistent volume across application upgrades.
`docker compose down` stops services; adding `--volumes` deletes database data
and is appropriate only for disposable test stacks.

For local development, start PostgreSQL, Redis, a trusted scanner, and Spring Boot, then run:

```bash
cd frontend
npm ci
API_PROXY_TARGET=http://localhost:8080 npm run dev
```

Vite proxies `/api`, `/ui`, `/swagger-ui`, and `/v3/api-docs`. Short links use
`APP_BASE_URL` and open the backend origin; arbitrary `/{code}` paths are not
proxied by Vite. `npm run preview` serves static assets only and is not a full
stack preview. Use the packaged app or Compose for end-to-end verification.

## Release checks

Every pull request runs `mvn clean verify`, including npm install, TypeScript
checking, lint, formatting, unit/component tests, the production build, and Java
unit/integration tests. Setup actions cache Maven artifacts and npm's download
cache keyed by the lockfile; runtime databases, `.env`, `node_modules`, and browser
reports are not dependency caches. Reports are separate workflow artifacts.

CI then builds and starts Compose, checks the runtime image and health, and runs
Playwright against that origin with real PostgreSQL. Tests create unique aliases
and clean up only their own successful creations. The redirect scenario uses the
public example.com as destination and intercepts short-link navigation to inspect the real 302 without following it.
The real application redirect and click accounting are exercised; DNS resolution
still requires network access. Existing
axe, keyboard, responsive, fault-state, and Lighthouse gates remain enabled.

To reproduce against an isolated stack (commands from the repository root):

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.ci.yml
export COMPOSE_PROJECT_NAME=urlshortener-e2e
export APP_PORT=18080 POSTGRES_PORT=25432 REDIS_PORT=26379 POSTGRES_DB=urlshortener_e2e
export APP_BASE_URL=http://localhost:18080 E2E_BASE_URL=http://localhost:18080
docker compose --env-file /dev/null up --build --wait --wait-timeout 180
python3 scripts/check-runtime-image.py "$(docker compose images -q app)"
cd frontend
npm ci
npx playwright install --with-deps chromium
export CHROME_PATH="$(node -p "require('@playwright/test').chromium.executablePath()")"
npm run test:e2e
npm run lighthouse
cd ..
# Only for this disposable test project:
docker compose --env-file /dev/null down --volumes --remove-orphans
```

The image check inspects the actual jar and verifies packaged HTML/JS/CSS,
absence of source maps and frontend sources, and absence of Node/npm and frontend
build directories in the runtime image. No new tool dependency is required.

## Troubleshooting

- **Missing Node / unsupported engine:** use Node 24 and Java 25. Maven invokes
  npm; Java alone cannot build the frontend. CI installs both toolchains.
- **Lockfile mismatch:** update dependencies with npm and commit the resulting
  lockfile. CI uses `npm ci`, which rejects inconsistent manifests.
- **Blank page or missing assets:** run `mvn clean verify`; do not use
  `-Dfrontend.skip=true` unless a Docker build stage supplied `frontend/dist`.
  Check that the reverse proxy preserves root-relative `/assets/` URLs and CSP.
- **API proxy errors:** check backend health and `API_PROXY_TARGET`; changing the
  public short-link origin does not change Vite's backend proxy target.
- **Links point to the wrong port/host:** set `APP_BASE_URL` and recreate the app
  container. This is runtime config, not a Vite environment variable.
- **Database startup failure:** check credentials and database name against the
  existing volume. PostgreSQL initialization variables apply to a fresh volume;
  changing them does not rename an existing database or change its password.
- **Port conflicts:** choose unused `APP_PORT` and `POSTGRES_PORT` and update
  `APP_BASE_URL` and `E2E_BASE_URL` consistently.
- **Browser executable missing:** run the Playwright install command above or
  supply `CHROME_PATH`. Inspect `frontend/playwright-report/` after failures.
- **Lighthouse failure:** inspect `frontend/lighthouse-report/`; run after browser
  tests with other builds stopped. Keep the checked-in thresholds intact.

## Screenshots

README screenshots are captured from the running production app at 1440px and
375px, in light mode with reduced motion, without creating any links. Regenerate
with `E2E_BASE_URL=... CHROME_PATH=... npm run screenshots --prefix frontend`.

## Local verification (2026-09-11)

`mvn clean verify` passed 264 Java tests and 71 frontend tests, including lint,
formatting, and the production build. An isolated Compose stack using a custom
database name passed the runtime image audit and all 20 Playwright tests. Six
Lighthouse runs scored Performance 93–95 and Accessibility, Best Practices, and
SEO 100. Screenshots were captured from that image. Hosted GitHub Actions has
not been run for these uncommitted changes; its commands were verified locally.

## Verification scope

Axe checks selected routes/themes against WCAG tags and rejects serious/critical
violations; this is not a complete accessibility conformance claim. Lighthouse
thresholds are automated samples. Dated counts are historical. Test the packaged
app with the explicit CI scanner overlay for disposable browser verification;
production requires a real scanner. The form/result may resize with content;
loading must remain stable, controls usable, and content free of horizontal overflow.
