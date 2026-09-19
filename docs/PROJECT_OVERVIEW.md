# Project Overview

This is a self-hosted URL shortener for operators and small teams. One layered
Spring Boot 4.0.0 application serves REST APIs, public redirects, a React SPA, and
Actuator. PostgreSQL is authoritative; Redis accelerates redirects, holds request
quotas, and caches safety verdicts. An operator-supplied HTTP scanner evaluates new
HTTP/HTTPS destinations before activation.

## Implemented scope

- ID-based Base62 codes, custom aliases, expiry, redirects, total clicks, and deletion.
- Deprecated anonymous V1 management and owner-scoped API-key V2 management.
- Operator provisioning, key rotation/revocation, usage tracking, and key audit.
- Redis cache-aside redirects, atomic opt-in distributed quotas, PNG/SVG QR images.
- Submission-time DNS/IP policy, fail-closed scanning, verdict cache, and safety audit.
- Responsive React V1/V2 creation and known-code analytics, authenticated QR, and paginated owner listing.
- OpenAPI, Actuator, ECS logging in Docker, Maven/CI/browser quality tooling.

## Partial and planned scope

F07 already has bounded owner listing and an index; API/dashboard filters remain.
F10 already has key/safety audit and optional V1 sunset; broader lifecycle audit,
alerts, restore drills, and enforced retirement remain. F04 bulk creation, F08
trends, and F09 link editing/deactivation are backlog. There is no public signup,
billing, or production deployment automation.

## Operating and contributing

Compose starts app, PostgreSQL, and Redis. A real scanner and deployment credentials
must be configured; startup health does not establish scan readiness. The CI-only
scanner fixture must never supply production reputation decisions. Existing links
are grandfathered as legacy-unscanned; submission safety is not continuous monitoring.

Read ARCHITECTURE.md for flows, API_REQUESTS.md for contracts, TICKETS.md for scoped
work, and VERIFICATION_FOLLOWUP.md for remaining verification. Maven verify runs
backend and frontend checks; browser/Lighthouse checks run separately against a
packaged application. AGENTS.md governs contributions. Keep the layered design and
existing libraries unless a concrete requirement justifies an addition.
