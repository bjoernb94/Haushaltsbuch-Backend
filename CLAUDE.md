# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend of a household-budget app ("Haushaltsbuch"). Docs, comments and the README are in German; keep it that way.

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

## Architecture

- Spring Boot 4.1, Spring Modulith modular monolith. Each direct subpackage of
  `de.bochmann.haushaltsbuch` (`buchungen`, `vertraege`, `versorgung`, `portfolio`, `nutzer`, `shared`)
  is a module, declared in its `package-info.java`. Only the module's root package is its public API;
  subpackages are internal. `shared` is `Type.OPEN`, so all modules may use its types.
  `ModularityTests` (`ApplicationModules.verify()`) fails on cyclic or internal cross-module access.
- Package by business domain, not by technical layer.
- Schema is managed only by Flyway (`src/main/resources/db/migration`); Hibernate runs with
  `ddl-auto: validate`, so every new entity needs a migration.
- Config is YAML-only (`application.yml`, profiles `dev` = default, `prod`). Keycloak settings are
  centralised under `haushaltsbuch.keycloak.*` and fed from env vars; `prod` has no defaults.
- Keycloak is currently disabled: all `spring.security.oauth2.*` config lives in the `keycloak`
  profile. Without it, `SecurityConfig` registers an open (permitAll) chain for local dev;
  `prod` pulls in `keycloak` via a profile group. `SecurityConfigTests` runs with `dev,keycloak`.
- Security with `keycloak` profile (`shared/security/SecurityConfig`): one filter chain combining BFF login
  (`oauth2Login`, session cookie, SPA CSRF cookie) and JWT resource server. `/api/**` requires
  auth and answers 401 instead of redirecting. Provider endpoints are configured explicitly
  (not via issuer discovery) so the app and tests start without Keycloak running.
  Keycloak roles are deliberately not mapped to authorities (no roles needed yet; see comment there).
- Access to business areas depends on the user's booked `Komponente` (stored in our DB, not as Keycloak
  roles): `SecurityConfig` maps `/api/buchungen|vertraege|versorgung|portfolio/**` to a component and
  checks it per request via `KomponentenAbfrage` (implemented in `nutzer`). New module endpoints must live
  under their module's path. Use `AngemeldeterNutzer.id(...)` for the user ID (`sub`); `Authentication#getName()`
  is `preferred_username` for session logins.
- Dev uses a locally running Postgres; Boot's Docker Compose support is off by default
  (`DOCKER_COMPOSE_ENABLED=true` to enable). `compose.yaml` runs PostgreSQL (app DB + separate `keycloak` DB created by
  `docker/postgres/init/`) and Keycloak on port 8180 (compose profile `keycloak`). Tests use Testcontainers instead
  (`TestcontainersConfiguration`), Docker Compose support is skipped in tests.
