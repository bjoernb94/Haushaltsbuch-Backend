# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend of a household-budget app ("Haushaltsbuch"). Docs, comments and the README are in German; keep it that way.

## Documentation

`README.md` is the entry point (setup, start, module overview with the Mermaid diagram). Details live in `docs/`:
`docs/api.md` (contract for the SPA: login, endpoints, error codes, money/date format, business endpoint rules),
`docs/entwicklung.md` (conventions: modules, household separation, entities, errors, DB, tests, Git),
`docs/betrieb.md` (Docker Compose, Keycloak setup, configuration, prod, system status) and
`docs/entscheidungen/` (ADRs; add a new one for architecture decisions instead of rewriting old ones).
Each topic lives in exactly one place; link instead of repeating. Update the docs together with the code.

## Commands

```bash
./mvnw verify                                       # full build incl. tests
./mvnw test -Dtest=ModularityTests                  # single test class
./mvnw test -Dtest=SecurityConfigTests#healthIsPublic  # single test method
./mvnw spring-boot:run                              # run locally (dev profile, local Postgres on :5432)
```

- JDK 25 (`~/.jdks/graalvm-jdk-25` on this machine; no `java` on PATH, set `JAVA_HOME`).
- The machine uses rootless Podman, not Docker: Testcontainers and Docker Compose support need
  `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`. Spring-context tests fail with
  "Could not find a valid Docker environment" otherwise. `ModularityTests` needs no containers.

- Shared IntelliJ run configs live in `.run/` (not `.idea/`, which is gitignored). The local dev DB is
  `jdbc:postgresql://localhost:5432/postgres` as user `haushaltsbuch` (see `.run/Run_Backend.run.xml`).
  `flyway-maven-plugin` is in the pom only for manual `flyway:info`/`flyway:validate` runs.

## Git workflow

- Never commit to `main` directly. Every change goes on its own branch and reaches `main` via pull request
  (build must be green). Branch names: `feature/<kurzname>`, `fix/<kurzname>`, `docs/<kurzname>`,
  `chore/<kurzname>`, prefixed with the issue number if there is one (e.g. `feature/14-haushalt`). Link the
  issue in the PR with `Closes #<n>`.
- Before starting work, create the branch from an up-to-date `main` if you are still on `main`.

## Architecture

- Spring Boot 4.1, Spring Modulith modular monolith. Each direct subpackage of
  `de.bochmann.haushaltsbuch` (`buchungen`, `vertraege`, `versorgung`, `portfolio`, `haushalt`, `nutzer`, `shared`)
  is a module, declared in its `package-info.java`. Only the module's root package is its public API;
  subpackages are internal. `shared` is `Type.OPEN`, so all modules may use its types.
  `ModularityTests` (`ApplicationModules.verify()`) fails on cyclic or internal cross-module access.
- Package by business domain, not by technical layer. Business modules split into topic subpackages (e.g.
  `vertraege.vertrag`, `vertraege.kategorie`, `vertraege.berechnung` for Spring-free calculations), each with its
  entity, repository, service, controller and DTOs; what other modules may use goes into the root package. Small
  modules without topics (`nutzer`) stay flat. See `docs/entwicklung.md` "Aufbau eines Fachmoduls".
- Schema is managed only by Flyway (`src/main/resources/db/migration`); Hibernate runs with
  `ddl-auto: validate`, so every new entity needs a migration. Enums: `VARCHAR(40)` + `@Enumerated(STRING)`, no
  `CHECK` constraint.
- Config is YAML-only (`application.yml`, profiles `dev` = default, `prod`). Keycloak settings are
  centralised under `haushaltsbuch.keycloak.*` and fed from env vars; `prod` has no defaults.
- Keycloak is always on, also in dev and tests: there is no `keycloak` profile and no open (permitAll) chain.
  The realm is versioned in `docker/keycloak/import/haushaltsbuch-realm.json` (imported via `--import-realm`, only
  if the realm doesn't exist yet; client secret from `KEYCLOAK_CLIENT_SECRET`, dev default `dev-secret` on both
  sides). Changes to realm settings belong in that file (and the reference steps in `docs/betrieb.md`).
- Security (`shared/security/SecurityConfig`): one filter chain combining session
  login and JWT resource server. There is no redirect to Keycloak (no `oauth2Login`): the SPA posts
  email/password to `POST /api/anmeldung`, `Anmeldung`/`KeycloakTokenClient` use Keycloak's password grant
  ("Direct access grants") and store the refresh token in the HTTP session (`KeycloakSitzung`);
  `SitzungErneuernFilter` refreshes it when the access token expires and ends the session if Keycloak
  refuses. `POST /api/abmeldung` returns 204 and also ends the Keycloak session. Registration logs the
  user in directly. Unauthenticated requests get 401; SPA CSRF cookie (`XSRF-TOKEN`). The token endpoint
  is configured explicitly (not via issuer discovery) so the app and tests start without Keycloak running.
  Keycloak roles are deliberately not mapped to authorities (no roles needed yet; see comment there).
  Open follow-ups are tracked as GitHub issues (with milestones), not in the docs.
- Access to business areas depends on the user's booked `Komponente` (stored in our DB, not as Keycloak
  roles): `SecurityConfig` maps `/api/buchungen|vertraege|versorgung|portfolio/**` to a component and
  checks it per request via `KomponentenAbfrage` (implemented in `haushalt`). New module endpoints must live
  under their module's path. Use `AngemeldeterNutzer.id(...)` for the user ID (`sub`) as UUID and
  `AngemeldeterNutzer.claims(...)` for name/email: `GET /api/nutzer/ich` reads them from the login token,
  not from Keycloak (users don't edit their data in Keycloak; a future profile area must update the session).
- Business data belongs to a household (module `haushalt`), not to a single user, so several users can share one
  Haushaltsbuch; booked components belong to the household (`haushalt_komponente`). Registration (`nutzer`) creates
  the household via `HaushaltService` in the same transaction as the user. Business tables store `haushalt_id`
  (no FK across modules), never `nutzer_id` as owner (audit columns `angelegt_von`/`geaendert_von` are fine). Another
  household's IDs must yield 404, not 403. Household separation is automatic: `FachEntity.haushaltId` is a Hibernate
  `@TenantId`, resolved by `HaushaltTenantResolver` from `HaushaltKontext` (request: logged-in user's household via
  `HaushaltAbfrage`, implemented in `haushalt` with JDBC since it runs while Hibernate opens a session; jobs/listeners:
  `HaushaltKontext.fuer(id, ...)` with the transaction started inside; none: `KEIN_HAUSHALT`, reads empty, saving
  fails). Don't filter business entities by hand; native SQL and tables with mixed rows (standard rows with
  `haushalt_id IS NULL`) aren't filtered and must use `HaushaltKontext.id()`. Each business module needs a test that
  another household gets 404 (helper `TestHaushalt`).
- Errors are `ProblemDetail` with a stable German `code` property (e.g. `NICHT_GEFUNDEN`, `EMAIL_BEREITS_VERGEBEN`);
  the SPA translates by code, `detail` is English and developer-only. Build them with `shared.fehler.Probleme.problem(...)`.
  `shared.fehler.FehlerBehandlung` (`@RestControllerAdvice`, extends `ResponseEntityExceptionHandler`) handles general
  errors centrally: validation (`VALIDIERUNG` with `felder` = field, constraint name, non-default annotation attributes),
  Spring MVC errors, `NichtGefundenException`, catch-all 500, and the security chain's 401/403/CSRF (entry point and
  access-denied handler delegate to `handlerExceptionResolver`). Module-specific errors stay as `@ExceptionHandler` in
  the module's controller with their own code. New codes go into the table in `docs/api.md`. Business checks in services
  (e.g. unknown/foreign category) throw `UngueltigeEingabeException(feld, code, detail)` → same `VALIDIERUNG` +
  `felder` format; field codes are PascalCase like constraint names (`Unbekannt`). Cross-field rules are class-level
  constraints whose validator attaches the violation to a field (`addPropertyNode`), otherwise `feld` is null.
- Money is always `shared.geld.Betrag` (value + `Currency`, `@Embedded`, columns `<feld>_wert NUMERIC(19, 2)` and
  `<feld>_waehrung VARCHAR(3)` via `ImplicitNamingStrategyComponentPathImpl`; JSON `{"wert": 12.34, "waehrung": "EUR"}`,
  value as number). Only EUR is allowed for now (`ErlaubteWaehrung`, foreign currencies in #30), but never assume
  one currency: arithmetic only within a currency, sums group by currency. Rounding `HALF_UP`, only in calculations.
  Request DTOs use `@NotNull @Valid Betrag`. Business dates are `LocalDate`; "today" is `LocalDate.now(clock)` with
  the `Clock` bean (Europe/Berlin); timestamps are `Instant`, stored as `TIMESTAMPTZ` in UTC. Test-only migrations
  live in `src/test/resources/db/migration` with versions ≥ 1000.
- Business endpoints: controllers take and return records (DTOs), never entities. `POST` → 201 + `Location` + body,
  `PUT` (whole object, no `PATCH`) → 200 + body, `DELETE` → 204. Business entities extend `shared.fachdaten.FachEntity`
  (UUID id, `@Version`, audit columns `angelegt_am/_von`, `geaendert_am/_von` filled by Spring Data auditing via
  `FachdatenConfig`; see `docs/entwicklung.md` for the SQL columns). DTOs expose `version`; on `PUT` call `entity.pruefeVersion(dto.version())`
  before changing fields (stale version → `KonfliktException`, 409 `KONFLIKT`; concurrent flushes are mapped to 409 too).
  Audit columns are stored but not exposed yet (#42). Paging only for potentially large lists (`Page` serialized
  `VIA_DTO`).
- `/actuator/health/systemstatus` (public, components without details) feeds the SPA login page. The `keycloak`
  health indicator (`shared/systemstatus/SystemstatusConfig`) caches its result for 10 s.
- Tests: Spring tests use `@IntegrationTest` (whole app, MockMvc, shared context) for web/security and cross-module
  flows, or `@ModuleTest` (`@ApplicationModuleTest` with `extraIncludes = "shared"`: Modulith otherwise drops all
  beans outside the module, incl. `FachdatenConfig` and `Clock`) for a module's services/repositories. Business
  modules use `@FachmodulTest` (`@ModuleTest` + mocks for `HaushaltAbfrage`/`KomponentenAbfrage`, which `haushalt`
  implements); tests set the household with `HaushaltKontext.fuer(...)` and must not be `@Transactional`. Pure
  calculations get plain unit tests. All annotations live in `src/test` root package. `@IntegrationTest` mocks all Keycloak access (`Identitaetsanbieter`,
  `KeycloakTokenClient`, bean `keycloakHealthIndicator`, which `KeycloakErreichbar` stubs `UP` before each test) and
  spies on `Anmeldung`; tests `@Autowired` these and stub with `when(...)`. Don't add per-class `@MockitoBean`s,
  properties or profiles without need: each variant is a new context. Create other modules' test data via their
  public API (e.g. `HaushaltService`), not mocks. One Postgres container is shared by all contexts
  (`TestcontainersConfiguration` starts it statically; not a `@ServiceConnection` bean, since Boot stops those when a
  context closes). `csrf()` from spring-security-test permanently swaps the CSRF repository in the cached context,
  so tests asserting the real `XSRF-TOKEN` cookie need a fresh context (see `CsrfCookieTests`).
- `ModularityTests` also writes Modulith docs (PlantUML/AsciiDoc) to `docs/module` (checked in, line order normalised);
  commit regenerated files and keep the Mermaid diagram in the README in sync when modules or dependencies change.
- Dev uses a locally running Postgres; Boot's Docker Compose support is off by default
  (`DOCKER_COMPOSE_ENABLED=true` to enable). `compose.yaml` runs PostgreSQL (app DB + separate `keycloak` DB created by
  `docker/postgres/init/`) and Keycloak on port 8180 with the realm import. Tests use Testcontainers instead
  (`TestcontainersConfiguration`), Docker Compose support is skipped in tests.
