# Verification follow-up — 2026-09-19

## Resolved in this change

- README/project overview describe the React SPA and implemented V2/F01/F02/F03/F05/F06.
- Architecture documents direct ID Base62, actual profiles, three Docker stages,
  active PostgreSQL migrations, JDBC repositories, and namespace-specific Redis privacy.
- F07/F10 are partial, with completed foundations separated from remaining work.
- Host/Compose quota defaults are now uniformly empty secret and zero limits;
  enabling quotas requires explicit valid policy. The checked-in secret is removed.
- Compose forwards REDIRECT_CACHE_PERMANENT_TTL. Provisioning examples no longer
  supply a password inconsistent with the database placeholder.
- Cached redirects compare destinations in the atomic database guard. Regression
  coverage checks stale cache after alias reuse and exactly one recorded click.
- Browser redirect test uses a public destination and intercepts the short-link navigation, fetches and checks the real 302 without
  following it, and preserves real API creation, redirect, and analytics.
- Java formatting and 85% coverage are documented as review conventions/targets,
  not nonexistent build gates. Accessibility scope is explicitly bounded.
- Safety failure persistence/alias reservation, V1 UI/V2 QR boundary, three QR quota
  calls, expiry-only status, fixed safety URL ceiling, and dormant CORS wiring are documented.
- Conflicting license declarations are removed pending the owner's choice. No
  license grant is invented and no LICENSE file is generated without that choice.

## Intentional remaining boundaries

No schema change, dashboard filters, URL editing, continuous scanning, new auth policy,
or permissive CORS support is introduced. Production should use same-origin deployment.
The fixed safety ceiling remains 2048 characters; raising only the service property
cannot raise it. Metadata still omits safety state. Existing frontend design files
remain uncommitted; this change does not stage or commit the owner's work.

## Reproducibility and deployment checklist

Floating image/action/runtime tags are not exact version pins. Before promoting a
release, record the Git commit, lockfile, Java/Maven/Node/npm versions, Docker Engine
and Compose versions, Python version, Chromium version, and image RepoDigests from
`docker image inspect`. Pin approved image digests in the release deployment after
verifying architecture and provenance. Do not substitute workstation versions for
unverified CI images. Hosted ubuntu-latest remains a moving environment; the full
CI run is the release evidence, not local test counts.

For production acceptance:

1. Configure a real trusted scanner and validate safe, malicious, timeout, malformed,
   oversized-response and DNS failure behavior. Never use docker-compose.ci.yml.
2. Verify HTTPS/public APP_BASE_URL, credentials, restricted DB/Redis/Actuator access,
   and reverse-proxy identity expectations. Health alone does not verify scanning.
3. Review flyway_schema_history against db/migration/postgres V1–V4 before upgrading;
   inactive root/MySQL V1 files must not be added to the migration scan location.
4. Set a fresh shared RATE_LIMIT_SECRET and positive limits before enabling quotas.
   Check management fail-closed and redirect fail-open behavior during Redis outage.
5. Run browser/Lighthouse against the packaged image with the explicit CI overlay
   only in a disposable isolated stack. Archive reports and image/runtime provenance.
6. Choose the project license and add the matching LICENSE and API metadata.

## Validation results

- `mvn verify`: passed full frontend/backend verification (435 backend and 81 frontend tests).
- Final `mvn verify -Dfrontend.skip=true`: passed 436 backend tests, no failures/errors/skips, after adding the quota-default regression and removing conflicting API license metadata.
- Final frontend lint and formatting checks: passed after the browser fixture correction.
- Python provisioning-script tests: 3 passed.
- Isolated Compose production build/startup: passed; application health UP.
- Runtime image audit: passed (9 production frontend entries, no Node/source maps).
- Compose config: a PT7M TTL override reaches the app; empty/zero quota defaults verified.
- Final complete Playwright suite: 20 passed, including real 302 inspection, click accounting, six widths, both themes, axe, keyboard, CSP, and failure states.
- Lighthouse: all configured gates passed across six runs (three home, three analytics). Home performance minimum 94; analytics minimum 94; accessibility, best practices, and SEO 100 in every run.

Initial Playwright run had 19 passes and one fixture failure: a route on the final
destination did not intercept the redirected request. The corrected fixture fetches
the real short-link response with maxRedirects=0, asserts 302/Location, then serves
a local confirmation page. It does not mock creation or increment analytics itself.

## Observed verification environment (not universal version pins)

| Component | Observed version / immutable image reference |
| --- | --- |
| Host Java / Maven | Ubuntu JDK 25.0.4 / Maven 3.9.12 |
| Host Node / npm | 24.19.0 / 11.17.0 |
| Host Docker / Compose | 29.8.1 / 5.5.1 |
| Host Python / browser | Python 3.14.4 / Google Chrome 153.0.8010.47 |
| PostgreSQL | 17.11; postgres@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73 |
| Redis | 8.10.1; redis@sha256:bd999b5cfee25fb24b8320a31fddbd69f462df44c8138c66e369582937beebc0 |
| Node scanner/build image | 24.21.0; node@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 |
| Maven build base | maven:3.9-eclipse-temurin-25@sha256:dd8e01b3be719853578c07b57ff8d9bbbbfe746f802226f05b19689420815221 |
| Runtime base | eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8 |

These references describe this build, not a claim that floating tags will resolve
the same way later. Local browser validation uses installed Chrome via CHROME_PATH;
GitHub CI installs Playwright Chromium, so hosted CI remains a separate validation.

## Resumed frontend work — 2026-09-19

The pending working tree adds API access selection, memory-only V2 credentials,
V2 creation/analytics/deletion, shared QR credentials, and paginated My links.
Access changes reset views; invalid credentials do not fall back to V1.
Legacy requests strip any inherited API-key header. README, architecture, project
overview, and F07 status now reflect this frontend. F07 filters remain backlog.
The earlier V1-only UI description and validation counts above are historical.

Fresh validation of the resumed working tree:

- `mvn verify`: BUILD SUCCESS; 436 Java tests and 94 frontend tests passed.
  No Java failures, errors, or skips; frontend build, lint, and formatting passed.
- Disposable Compose build/startup: passed with the explicit CI scanner fixture.
- Playwright: all 22 tests passed, including the new mobile/desktop owner flows.
- Lighthouse: all configured gates passed over six runs; minimum performance
  93 on home and 94 on analytics, with accessibility, best practices, and SEO 100.
- README desktop/mobile screenshots refreshed from the packaged application.
- `git diff --check`: passed. Existing changes remain uncommitted.

The initial build exposed unsupported `exact` options in the new Testing Library
queries; these were removed. The analytics loader now tracks the selected API key
through a stable callback, eliminating the missing-effect-dependency warning.
Earlier chats were not available in this session; the unfinished work was identified
from recent commits, pending files, and repository follow-up notes.
