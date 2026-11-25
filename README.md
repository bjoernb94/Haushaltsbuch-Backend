# Haushaltsbuch-Backend

Haushaltsbuch ist eine selbst gehostete Webanwendung, in der ein Haushalt seine Finanzen gemeinsam verwaltet:
Ein- und Ausgaben, Verträge, Strom, Gas und Wasser sowie Geldanlagen. Fertig ist bisher der Bereich
Verträge (Preise und Preisänderungen, Anteile, Einmalkosten, Kündigungsfristen und Prognose der Kosten);
die anderen Bereiche sind geplant. Gedacht ist sie vorerst für das Heimnetz, der Betrieb im Internet folgt
mit v1.0.0.

Dieses Repo enthält das Backend: einen modularen Monolithen auf Basis von Spring Boot, mit PostgreSQL als
Datenbank und Keycloak als Identity Provider. Die Oberfläche ist eine Angular-SPA im Repo
[Haushaltsbuch-Frontend](https://github.com/bjoernb94/Haushaltsbuch-Frontend); sie spricht nur mit dem Backend
([API](docs/api.md)), das auch die Anmeldung bei Keycloak übernimmt. Wer die Anwendung betreiben möchte,
findet unter [Betrieb per Compose](docs/betrieb.md#betrieb-per-compose), wie sie mit allen Diensten aus den
veröffentlichten Images startet.

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
zusätzlich `TESTCONTAINERS_RYUK_DISABLED=true` setzen. Das Image (`./mvnw spring-boot:build-image`) baut
unter rootless Podman nur mit `-Dimage.podman=true`; dann bekommen die Buildpacks den Socket aus `DOCKER_HOST`.

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

1. PostgreSQL und Keycloak starten: Run Configuration „Umgebung starten“ oder `podman compose up -d` (siehe
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

## Veröffentlichen

Ein Release entsteht aus einem Tag auf `main`, nicht von Hand ([ADR 0015](docs/entscheidungen/0015-release-prozess.md)).
Der Workflow `.github/workflows/release.yml`
prüft, dass der Tag zur Version in der `pom.xml` passt und `betrieb/compose.yaml` Backend und Frontend in
dieser Version startet. Außerdem verlangt er einen offenen Milestone mit genau dem Namen des Tags ohne offene
Issues (auch für Bugfix-Releases wie `v0.1.1`); sonst bricht er ab und nennt die offenen Issues. Dann baut und
testet er wie die CI (`build.yml`), veröffentlicht das Image als `ghcr.io/bjoernb94/haushaltsbuch-backend`,
legt das GitHub Release mit generierten Notes an (gruppiert nach den Labels der Pull Requests, siehe
`.github/release.yml`) und schließt den Milestone. Am Release hängen `docs/openapi.json` und das Paket für den
Betrieb per Compose ([Betrieb per Compose](docs/betrieb.md#betrieb-per-compose)).

1. Version per Pull Request anheben: `<version>` in der `pom.xml` und die beiden Image-Versionen in
   `betrieb/compose.yaml`.
2. Nach dem Merge auf `main` taggen: `git tag v0.2.0 && git push origin v0.2.0`.
3. Braucht eine bestehende Instanz beim Update einen Schritt von Hand (z.&nbsp;B. in Keycloak), ihn danach in die
   Release Notes eintragen (`gh release edit v0.2.0 --notes-file …`); die allgemeinen Schritte stehen unter
   [Update](docs/betrieb.md#update).

Backend und Frontend zählen gleich hoch; das Backend wird zuerst veröffentlicht. Solange die Hauptversion 0
ist, ist jedes Release eine Vorabversion (Pre-release) und das Image bekommt nur die Tags `0.2.0` und `0.2`;
ab 1.0 kommt `latest` dazu. Die Images sind öffentlich und nur für amd64.

## Dokumentation

| Datei                                                  | Inhalt                                                                         |
|--------------------------------------------------------|--------------------------------------------------------------------------------|
| [`docs/api.md`](docs/api.md)                           | API für die SPA: Anmeldung, Endpunkte, Fehlercodes, Geldbeträge, Fachendpunkte |
| [`docs/entwicklung.md`](docs/entwicklung.md)           | Konventionen: Module, Fachdaten, Trennung der Haushalte, Datenbank, Tests, Git |
| [`docs/betrieb.md`](docs/betrieb.md)                   | Docker Compose, Betrieb per Compose, Keycloak, Konfiguration, prod, Systemstatus |
| [`docs/entscheidungen/`](docs/entscheidungen/README.md) | Architekturentscheidungen mit Begründung, auch die des Frontends               |
| [`docs/openapi.json`](docs/openapi.json)               | von `OpenApiExportTests` erzeugte OpenAPI-Beschreibung der API                 |
| [`docs/module/`](docs/module)                          | von `ModularityTests` erzeugte Modul-Dokumentation                             |

Die Dokumentation liegt nur in den Repos, ein Wiki gibt es nicht: So wird sie im selben Pull Request wie der
Code geändert und geprüft und passt zu jedem Release-Tag ([ADR 0021](docs/entscheidungen/0021-doku-im-repo.md)).
Die Oberfläche beschreibt das Frontend in
[`docs/entwicklung.md`](https://github.com/bjoernb94/Haushaltsbuch-Frontend/blob/main/docs/entwicklung.md),
Image und `instanz.json` in
[`docs/betrieb.md`](https://github.com/bjoernb94/Haushaltsbuch-Frontend/blob/main/docs/betrieb.md).

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
├── nutzer       Registrierung, Konto in Keycloak
└── shared       Security, Fehlerbehandlung, Fach-Entities und Trennung der Haushalte (fachdaten),
                 Betrag (geld), Clock (zeit), Systemstatus, OpenAPI-Beschreibung (openapi),
                 gemeinsame Typen (z. B. Komponente)
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
- `betrieb/compose.yaml`: Backend, Frontend, PostgreSQL und Keycloak für den Betrieb, siehe
  [Betrieb per Compose](docs/betrieb.md#betrieb-per-compose)
- `docker/keycloak/import/`: Realm `haushaltsbuch` für den Import beim Start von Keycloak
- `docker/postgres/init/`: Init-Skript, das die Keycloak-Datenbank anlegt
- `docs/`: Dokumentation, siehe [oben](#dokumentation)
- `scripts/testdaten.py`: legt lokale Testkonten an, siehe [Testkonten](docs/entwicklung.md#testkonten)
- `scripts/betrieb-paket.sh`: packt das Paket für den Betrieb per Compose, das am Release hängt
- `.run/`: gemeinsame IntelliJ Run Configurations, siehe
  [IntelliJ Run Configurations](docs/entwicklung.md#intellij-run-configurations)
- `.github/workflows/build.yml`: Build und Tests in GitHub Actions
- `.github/workflows/release.yml`: der Release-Workflow, siehe [Veröffentlichen](#veröffentlichen)
- `.github/release.yml`: kein Workflow, sondern die Gruppen der generierten Release Notes nach den Labels
  der Pull Requests
- `.github/dependabot.yml`: Updates der Abhängigkeiten per Pull Request (Maven wöchentlich, GitHub Actions und
  Images monatlich)
- `.github/pull_request_template.md`: Vorlage für Pull Requests, siehe
  [Arbeitsweise mit Git](docs/entwicklung.md#arbeitsweise-mit-git)

## Offene Aufgaben

Geplante Erweiterungen und bekannte Lücken (z.&nbsp;B. „Passwort vergessen“, Profilbereich,
Keycloak für prod) werden als [GitHub Issues](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues)
gepflegt und sind Meilensteinen zugeordnet.

## Lizenz

Copyright © 2026 Björn Bochmann. Veröffentlicht unter der
[GNU Affero General Public License v3.0](LICENSE) oder einer späteren Version (`AGPL-3.0-or-later`).
Wer das Backend verändert und anderen über ein Netzwerk bereitstellt, muss ihnen den geänderten
Quellcode zugänglich machen (Abschnitt 13 der Lizenz).
