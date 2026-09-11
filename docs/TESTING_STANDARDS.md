# Testing Standards

## Stack
- **JUnit 6** (Jupiter) as the test engine.
- **Mockito** for mocking collaborators in unit tests.
- **AssertJ** for fluent, readable assertions (`assertThat(...)`, not raw JUnit `assertEquals`).
- **Testcontainers** (Postgres module) for repository/integration tests — no H2, so tests run against the real database engine.
- **Spring Boot Test** slices: `@WebMvcTest` for controllers, `@DataJpaTest` for repositories, `@SpringBootTest` only for full end-to-end flows.

## Test types and where they live
| Type | Annotation/tooling | Scope | Package |
|---|---|---|---|
| Unit | plain JUnit + Mockito | one class, all collaborators mocked | mirrors `main` package |
| Repository/integration | `@DataJpaTest` + Testcontainers | repository ↔ real Postgres | `repository` |
| Controller/web slice | `@WebMvcTest` + `MockMvc` | HTTP layer only, service mocked | `controller` |
| End-to-end | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers | full stack, one or two happy/critical paths only | top-level `e2e` |

Tag unit tests with JUnit's `@Tag("unit")` and integration/e2e with `@Tag("integration")` so `mvn test -Dgroups=unit` can run the fast set alone (see AGENTS.md §4).

## Naming convention
`methodUnderTest_stateOrInput_expectedBehavior()`, e.g.:
- `shorten_validUrlNoAlias_returnsGeneratedCode()`
- `shorten_aliasAlreadyTaken_throwsDuplicateAliasException()`
- `resolve_expiredCode_throwsUrlExpiredException()`

## Structure
Arrange / Act / Assert, with blank lines separating the three (no explicit comments needed once this is a habit). One logical assertion concern per test — multiple `assertThat` calls checking facets of the *same* outcome are fine; testing two unrelated behaviors in one test method is not.

## Coverage expectations
- Every public method on every `service`, `util`, and `repository` custom-query class has at least: one happy-path test, one test per distinct exception/edge case it can produce.
- Every controller endpoint has at least: one 2xx test, one validation-failure (4xx) test, one "service throws domain exception → correct status" test.
- `GlobalExceptionHandler` has a dedicated test class asserting each exception → status/body mapping.
- Line coverage target: 85%+ on `service` and `util` packages (enforced via `jacoco-maven-plugin`, not a hard gate on `controller`/`domain`).

## What NOT to do
- No `Thread.sleep` for timing — use Awaitility or design the code to be testable without real delays.
- No shared mutable state between tests; each test builds its own fixtures (`@BeforeEach`, not static shared instances).
- No testing framework internals (don't assert Spring wired something correctly — assert behavior).
- No asserting on log output as a substitute for asserting on real return values/exceptions.

## Fixtures
Use small builder/factory helpers in a `test/.../util` package (e.g., `ShortUrlTestFixtures.aShortUrl()`), not copy-pasted object construction across test classes.

## Frontend

Vitest, React Testing Library, and user-event test visible behavior and keyboard
interaction in jsdom. Run `npm test` from `frontend/`; tests use the same
`Component_condition_expectedBehavior` naming style. Maven verification also
runs frontend lint, formatting checks, and tests. `FrontendProductionTest`
requests the generated HTML and referenced JavaScript/CSS from a real Spring
Boot HTTP server with Testcontainers PostgreSQL, and checks that unknown short
codes still return the API's 404 response. Native dialog focus containment and
responsive appearance should additionally be checked in a real browser.


Production UI quality gates use Playwright plus axe and Lighthouse CI; see
`FRONTEND_QUALITY.md`. Browser tests operate against an isolated running app,
create their own uniquely named fixtures, and delete only those fixtures.
Fault-state tests intercept requests instead of requiring production outages.
Lighthouse runs sequentially after functional tests to reduce measurement noise.
