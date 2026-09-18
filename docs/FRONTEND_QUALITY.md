# Frontend quality checks

TICKET-018 adds production browser checks and Lighthouse CI thresholds. These
checks target a running local/test Spring Boot deployment, so they exercise the
real CSP and packaged assets. They create uniquely named `qa...` links and clean
up only successfully created fixtures. No report is uploaded to a public service.

## Commands

After `mvn verify`, run the packaged application with a test PostgreSQL database.
From `frontend/`:

```bash
npx playwright install --with-deps chromium
export E2E_BASE_URL=http://127.0.0.1:8080
export CHROME_PATH="$(node -p "require('@playwright/test').chromium.executablePath()")"
npm run test:e2e
npm run lighthouse
```

An existing Chromium/Chrome executable can be supplied through `CHROME_PATH`.
The existing CI workflow runs these checks against its isolated Compose stack
and retains local reports as workflow artifacts. The new development-only
packages are `@playwright/test` (browser workflows), `@axe-core/playwright`
(accessibility scans), and `@lhci/cli` (performance/quality thresholds). They do
not enter the production JavaScript bundle.

## Coverage and thresholds

- Responsive create/analytics/modal checks: 320, 375, 768, 1024, 1440, 1920px.
- axe scans in light and dark mode: no serious or critical WCAG violations on
  create, populated analytics, and confirmation dialog states.
- Keyboard-only creation, clipboard copy, analytics lookup, modal focus wrapping,
  Escape dismissal/restoration, cancellation, and confirmed deletion.
- Real CSP rejects injected inline scripts; the skip link preserves analytics
  routing; reduced-motion styles remain active.
- Offline, 15-second timeout (including stalled response bodies), malformed JSON,
  HTTP 429, and HTTP 5xx preserve input and offer safe recovery messages.
- Lighthouse CI collects three mobile runs for each route, using the lowest category
  scores across all runs (hash routes share a Lighthouse assertion group) with error-level thresholds: Accessibility 95, Best Practices 95,
  SEO 90, Performance 85. Reports are written to `lighthouse-report/` and
  `.lighthouseci/`; no assertions are disabled to obtain a passing score.

Automated scans are complemented by keyboard and focus checks; they do not
constitute a formal accessibility certification or exhaustive assistive-technology
coverage. Browser reports live in `playwright-report/` and `test-results/`.

## Runtime hardening

`FrontendController` supplies a fresh cryptographically random script nonce per
HTML response and attaches a strict CSP using `strict-dynamic`. Scripts are
external; no inline script or `unsafe-inline`/`unsafe-eval` exception is used.
Styles, fonts, images, and connections are same-origin, with framing, objects,
and base-URL changes forbidden. The policy applies to the frontend HTML rather
than breaking the independently served Swagger UI. The theme bootstrap lives
at `/assets/theme.js`. The no-cache HTML response discovers each build's assets.

`apiRequest` buffers response bodies within a 15-second deadline, forwards route
cancellation, and never automatically retries mutations. Typed failures expose
only fixed recovery messages. An unconfirmed creation/deletion may have reached
the server; the UI explains this and leaves retry to the user. `ApiErrorBoundary`
contains unexpected rendering errors without displaying exception details.

Dialogs trap Tab/Shift+Tab and close with Escape. Before deletion confirmation,
closing does nothing to the link. After confirmation, closing only dismisses
the dialog; the submitted request continues and the page reports its outcome.
Stable workflow slots and skeletons reserve space during asynchronous loading.

References: [Playwright accessibility testing](https://playwright.dev/docs/accessibility-testing)
and [Lighthouse CI configuration](https://github.com/GoogleChrome/lighthouse-ci/blob/main/docs/configuration.md).

## Verified locally (2026-09-11)

All 16 production browser tests passed. All six Lighthouse runs scored
Performance 95, Accessibility 100, Best Practices 100, and SEO 100.
`mvn verify` passed 261 Java tests and 71 frontend tests.

TICKET-019 adds release workflows and an image-content audit; see
[Frontend deployment](FRONTEND_DEPLOYMENT.md) for containerized reproduction
and the analytics hash-route clarification.


Link creation now requires scanning. For an isolated browser-test deployment, use
`docker compose -f docker-compose.yml -f docker-compose.ci.yml up --build --wait`.
The explicit CI overlay adds a deterministic scanner fixture accepting only HTTPS
`example.com` destinations; it is not a production reputation service. CI uses
this overlay for both startup and cleanup. Production must configure a real
provider using the contract in `ARCHITECTURE.md`.
