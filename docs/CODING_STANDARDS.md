# Coding Standards

These apply to every PR, human or AI-authored. A PR that violates these should not be merged without a documented reason.

## Language & style
- **Java 25**, use modern features where they improve clarity: records for DTOs, pattern matching for `switch`/`instanceof`, `var` for obvious local types only (never for method return types in public APIs).
- **Constructor injection only.** No `@Autowired` on fields. Services depend on interfaces, not concrete implementations, where more than one implementation is plausible.
- **Immutability by default**: DTOs are `record`s; entity setters are only present where JPA requires them.
- **No wildcard imports.**
- Formatting convention: Google Java Format or equivalent. No Spotless/Checkstyle Maven gate is currently configured; review Java style manually. Frontend formatting is enforced by Prettier.

## Package structure
Package-by-layer (`controller`, `service`, `repository`, `domain`, `dto`, `exception`, `config`, `util`) — see `docs/ARCHITECTURE.md` §3. Do not introduce package-by-feature without updating that document first.

## Naming
- Classes: `UpperCamelCase`, interfaces named for what they do (`UrlShortenerService`), not prefixed with `I`.
- Implementations: `<Interface>Impl` (`UrlShortenerServiceImpl`).
- DTOs: suffix `Request`/`Response` (`CreateShortUrlRequest`, `ShortUrlResponse`).
- Exceptions: suffix `Exception`, one per distinct failure mode, no generic `ServiceException` catch-all.

## Null-safety
- Repositories return `Optional<T>` for "may not exist" lookups.
- Services never return `null`; they either return a value or throw a domain exception.
- Public methods document (`@param`/`@return`/`@throws` in Javadoc) any precondition that isn't obvious from the type signature.

## Logging
- SLF4J only, `private static final Logger log = LoggerFactory.getLogger(ClassName.class)`.
- Log at `info` for business events (URL created, URL resolved), `warn` for recoverable issues (alias collision retried), `error` only for unexpected failures. Never log the full original URL at `error` level if it could contain sensitive query params — log the short code instead.

## Validation
- All external input validated at the DTO boundary with `jakarta.validation` annotations (`@NotBlank`, `@Pattern`, `@Future`, custom `@ValidUrl` if needed).
- Business-rule validation (e.g., alias uniqueness) happens in the service layer, not in the DTO.

## Database
- Every schema change is a new Flyway migration file, never edit a shipped migration.
- No `SELECT *`; JPA projections/entities are explicit about columns via mapped fields.
- Indexes documented in `ARCHITECTURE.md` when added.

## API design
- REST, plural nouns, versioned base path `/api/v1`.
- Use correct HTTP status codes (see `API_REQUESTS.md`) — don't return `200` with an error payload.
- Breaking changes to a response shape require a version bump (`/api/v2`), not an in-place change.

## Dependency hygiene
- New dependency in `pom.xml` → call it out explicitly in the PR description with a one-line justification.
- Keep `pom.xml` dependencies to what's actually used; remove unused ones in the same PR that made them unused.

## Documentation
- Every public class/interface: one-line Javadoc describing its responsibility (not its name restated).
- Every public method with non-obvious behavior: Javadoc with `@throws` for domain exceptions it can raise.
- README, ARCHITECTURE.md, and API_REQUESTS.md updated in the same PR as the code change they describe — docs drift is treated as a bug.
