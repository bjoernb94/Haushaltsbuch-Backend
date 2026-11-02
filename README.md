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
PostgreSQL-Instanz auf `localhost:5432` und Keycloak auf `localhost:8180` (Zugangsdaten und
Defaults siehe [Konfiguration](docs/betrieb.md#konfiguration)). Backend: http://localhost:8080, Swagger UI unter
`/swagger-ui.html`.

Keycloak ist immer angebunden, auch lokal; eine offene Konfiguration ohne Anmeldung gibt es
nicht. Das Backend startet auch ohne laufenden Keycloak, Anmeldung und Registrierung schlagen
dann aber fehl, und der [Systemstatus](docs/betrieb.md#systemstatus) meldet `keycloak` als `DOWN`.

### Lokal mit Keycloak

1. Keycloak starten, z.&nbsp;B. per `docker compose up -d keycloak` (siehe
   [Docker Compose](docs/betrieb.md#docker-compose)). Der Realm wird beim ersten Start automatisch eingerichtet,
   siehe [Keycloak einrichten](docs/betrieb.md#keycloak-einrichten).
2. Backend starten: `./mvnw spring-boot:run`. Das Client-Secret ist in dev mit dem Realm-Import
   abgestimmt (`dev-secret`) und muss nicht gesetzt werden.
3. Konto über `POST /api/registrierung` anlegen (siehe [Registrierung](docs/api.md#registrierung)), danach
   Anmeldung testen, z.&nbsp;B. mit curl (CSRF-Token aus dem Cookie holen, dann anmelden):

   ```bash
   curl -s -c /tmp/kc.txt http://localhost:8080/actuator/health >/dev/null
   XSRF=$(awk '/XSRF-TOKEN/{print $7}' /tmp/kc.txt)
   curl -i -b /tmp/kc.txt -c /tmp/kc.txt -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
     -d '{"email":"max@example.org","passwort":"Geheim123!"}' http://localhost:8080/api/anmeldung
   curl -b /tmp/kc.txt http://localhost:8080/api/nutzer/ich/komponenten
   ```

Testkonten mit Verträgen für alle fachlichen Zustände legt `python3 scripts/testdaten.py` an, siehe
[Testkonten](docs/entwicklung.md#testkonten).

Build und Tests: `./mvnw verify` (braucht eine Container-Umgebung für Testcontainers), Details
unter [Build und Tests](docs/entwicklung.md#build-und-tests).

## Dokumentation

| Datei                                                  | Inhalt                                                                         |
|--------------------------------------------------------|--------------------------------------------------------------------------------|
| [`docs/api.md`](docs/api.md)                           | API für die SPA: Anmeldung, Endpunkte, Fehlercodes, Geldbeträge, Fachendpunkte |
| [`docs/entwicklung.md`](docs/entwicklung.md)           | Konventionen: Module, Fachdaten, Trennung der Haushalte, Datenbank, Tests, Git |
| [`docs/betrieb.md`](docs/betrieb.md)                   | Docker Compose, Keycloak einrichten, Konfiguration, prod, Systemstatus         |
| [`docs/entscheidungen/`](docs/entscheidungen/README.md) | Architekturentscheidungen mit Begründung                                       |
| [`docs/openapi.json`](docs/openapi.json)               | von `OpenApiExportTests` erzeugte OpenAPI-Beschreibung der API                 |
| [`docs/module/`](docs/module)                          | von `ModularityTests` erzeugte Modul-Dokumentation                             |

## Projektstruktur

Paketiert nach Fachbereichen, nicht nach technischen Schichten. Jedes direkte Unterpaket
von `de.bochmann.haushaltsbuch` ist ein Spring-Modulith-Modul:

```
de.bochmann.haushaltsbuch
├── buchungen    Ein-/Ausgaben, Kategorien
├── vertraege    Verträge, Laufzeiten, Kündigungsfristen
├── versorgung   Strom, Gas, Wasser, Abschläge, Zählerstände
├── portfolio    Depots, Positionen, Kurse
├── haushalt     Haushalt, Mitglieder, gebuchte Komponenten
├── nutzer       Registrierung, Profil, Konto in Keycloak
└── shared       Security, Fehlerbehandlung, Fach-Entities und Trennung der Haushalte (fachdaten),
                 Betrag (geld), Clock (zeit), Systemstatus, gemeinsame Typen (z. B. Komponente)
```

### Module und Abhängigkeiten

```mermaid
flowchart LR
    nutzer[Nutzer] --> haushalt[Haushalt]
    nutzer --> shared[Shared]
    haushalt --> shared
    buchungen[Buchungen] -.-> shared
    vertraege[Verträge] --> shared
    versorgung[Versorgung] -.-> shared
    portfolio[Portfolio] -.-> shared
```

Gestrichelt: Module ohne Code bisher, die `shared` nutzen dürfen. Das Diagramm ist von Hand
gepflegt; maßgeblich sind die Diagramme, die `ModularityTests` bei jedem Testlauf mit dem
Documenter von Spring Modulith nach [`docs/module`](docs/module) schreibt (PlantUML, z.&nbsp;B.
`components.puml` für den Überblick und `module-<name>.puml` je Modul, dazu AsciiDoc mit den
Komponenten je Modul). Die Dateien sind eingecheckt: Ändern sich Module oder Abhängigkeiten,
erscheint das im Diff und gehört mit in den Commit; dann auch das Diagramm oben anpassen. Die
GitHub Action prüft nach dem Build, ob `docs/module` dem eingecheckten Stand entspricht, und
schlägt sonst fehl.

Die Regeln für Module und ihren Aufbau stehen unter [Entwicklung](docs/entwicklung.md#module).

Weitere Dateien:

- `compose.yaml`: PostgreSQL und Keycloak für die lokale Entwicklung
- `docker/keycloak/import/`: Realm `haushaltsbuch` für den Import beim Start von Keycloak
- `docker/postgres/init/`: Init-Skript, das die Keycloak-Datenbank anlegt
- `docs/`: Dokumentation, siehe [oben](#dokumentation)
- `.github/workflows/build.yml`: Build und Tests in GitHub Actions

## Offene Aufgaben

Geplante Erweiterungen und bekannte Lücken (z.&nbsp;B. „Passwort vergessen“, Profilbereich,
Keycloak für prod) werden als [GitHub Issues](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues)
gepflegt und sind Meilensteinen zugeordnet.
