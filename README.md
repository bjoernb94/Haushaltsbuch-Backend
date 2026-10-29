# Haushaltsbuch-Backend

Backend der Haushaltsbuch-Anwendung: ein modularer Monolith auf Basis von Spring Boot,
mit PostgreSQL als Datenbank und Keycloak als Identity Provider.

## Voraussetzungen

- JDK 25
- Docker oder Podman inkl. Docker Compose (`docker compose` oder `docker-compose`)
- Maven muss nicht installiert sein, der Maven Wrapper (`./mvnw`) ist enthalten.

### Hinweis für Podman

Testcontainers und der Docker Compose Support erwarten eine Docker-kompatible API.
Bei Podman den Socket aktivieren und `DOCKER_HOST` setzen:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
```

Falls der Ryuk-Container von Testcontainers unter rootless Podman nicht startet,
zusätzlich `TESTCONTAINERS_RYUK_DISABLED=true` setzen.

## Starten

```bash
./mvnw spring-boot:run
```

Ohne gesetztes Profil läuft die Anwendung im Profil `dev` gegen eine lokal laufende
PostgreSQL-Instanz auf `localhost:5432` (Zugangsdaten siehe [Konfiguration](#konfiguration)).
Backend: http://localhost:8080, Swagger UI unter `/swagger-ui.html`.

### Keycloak (vorerst deaktiviert)

Ohne das Profil `keycloak` ist die Security im dev-Profil offen: alle Endpunkte sind ohne
Anmeldung erreichbar. In `prod` ist `keycloak` über eine Profilgruppe immer aktiv.

Zum Aktivieren:

1. Keycloak starten: `docker compose --profile keycloak up -d` (Admin-Konsole unter
   http://localhost:8180, Dev-Zugang `admin` / `admin`).
2. In der Admin-Konsole den Realm `haushaltsbuch` und einen Confidential Client
   `haushaltsbuch-backend` mit Standard Flow anlegen, Redirect-URI
   `http://localhost:8080/login/oauth2/code/keycloak`.
3. Mit `SPRING_PROFILES_ACTIVE=dev,keycloak` und `KEYCLOAK_CLIENT_SECRET=<secret>` starten.

### Docker Compose

`compose.yaml` enthält PostgreSQL (App-Datenbank plus separate `keycloak`-Datenbank) und
Keycloak. Der automatische Start durch Spring Boot ist vorerst aus, weil eine eigene
PostgreSQL-Instanz genutzt wird; einschalten mit `DOCKER_COMPOSE_ENABLED=true`
(dann darf auf Port 5432 nichts anderes laufen).

## Konfiguration

Die Konfiguration liegt ausschließlich in `src/main/resources/application.yml`
(Profile `dev` und `prod`). Werte kommen aus Umgebungsvariablen; im Profil `dev` gibt es
Defaults für die lokale Entwicklung, im Profil `prod` sind alle Variablen Pflicht.

| Variable                 | Dev-Default                                     |
|--------------------------|-------------------------------------------------|
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/haushaltsbuch` |
| `DB_USERNAME`            | `haushaltsbuch`                                 |
| `DB_PASSWORD`            | `haushaltsbuch`                                 |
| `KEYCLOAK_ISSUER_URI`    | `http://localhost:8180/realms/haushaltsbuch`    |
| `KEYCLOAK_CLIENT_ID`     | `haushaltsbuch-backend`                         |
| `KEYCLOAK_CLIENT_SECRET` | leer                                            |
| `DOCKER_COMPOSE_ENABLED` | `false`                                         |

Die `KEYCLOAK_*`-Variablen werden nur mit Profil `keycloak` ausgewertet.

Produktiv starten mit `SPRING_PROFILES_ACTIVE=prod`. Dort sind Keycloak immer aktiv, der Docker Compose Support
sowie Swagger UI und die OpenAPI-Dokumentation deaktiviert.

Das Datenbankschema wird ausschließlich über Flyway (`src/main/resources/db/migration`)
gepflegt; Hibernate prüft es nur (`ddl-auto: validate`).

## Build und Tests

```bash
./mvnw verify                                     # Build inkl. aller Tests
./mvnw test -Dtest=ModularityTests                # einzelne Testklasse
./mvnw test -Dtest=SecurityConfigTests#healthIsPublic   # einzelne Testmethode
```

Integrationstests starten PostgreSQL per Testcontainers und benötigen daher eine laufende
Container-Umgebung. `ModularityTests` prüft die Modulstruktur mit Spring Modulith.

Zum lokalen Start mit Testcontainers statt Docker Compose:
`TestHaushaltsbuchBackendApplication` (unter `src/test`) ausführen.

### IntelliJ Run Configurations

Unter `.run/` liegen gemeinsame Run Configurations (Spring-Boot-Start sowie Maven-Aufrufe für
Build, Tests, Dependency-Checks, Image-Build und Flyway). Sie enthalten die lokalen Dev-Werte
(DB `postgres` auf `localhost:5432`, Podman-Socket unter `/run/user/1000`) und müssen auf anderen
Rechnern ggf. angepasst werden. `flyway:info` / `flyway:validate` arbeiten direkt gegen die
lokale Datenbank; die Migration selbst führt Spring Boot beim Start aus.

## Projektstruktur

Paketiert nach Fachbereichen, nicht nach technischen Schichten. Jedes direkte Unterpaket
von `de.bochmann.haushaltsbuch` ist ein Spring-Modulith-Modul:

```
de.bochmann.haushaltsbuch
├── buchungen    Ein-/Ausgaben, Kategorien
├── vertraege    Verträge, Laufzeiten, Kündigungsfristen
├── versorgung   Strom, Gas, Wasser, Abschläge, Zählerstände
├── portfolio    Depots, Positionen, Kurse
├── nutzer       Profil, Einstellungen, gewählte Komponenten
└── shared       Security-Konfiguration, Fehlerbehandlung, gemeinsame Typen
```

Weitere Dateien:

- `compose.yaml`: PostgreSQL und Keycloak für die lokale Entwicklung
- `docker/postgres/init/`: Init-Skript, das die Keycloak-Datenbank anlegt
