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

### Ohne Keycloak

Ohne das Profil `keycloak` ist die Security im dev-Profil offen: alle Endpunkte sind ohne
Anmeldung erreichbar, auch die Fachbereiche ohne gebuchte Komponente. Die Registrierung legt
dann kein Konto in Keycloak an, sondern vergibt nur eine zufällige ID; ein Login ist nicht
möglich. In `prod` ist `keycloak` über eine Profilgruppe immer aktiv.

### Mit Keycloak

1. Keycloak starten und einmalig einrichten, siehe [Keycloak einrichten](#keycloak-einrichten).
2. Backend mit Profil `keycloak` und dem Client-Secret starten:

   ```bash
   SPRING_PROFILES_ACTIVE=dev,keycloak KEYCLOAK_CLIENT_SECRET=<secret> ./mvnw spring-boot:run
   ```
3. Login testen: http://localhost:8080/oauth2/authorization/keycloak leitet zur Anmeldeseite
   von Keycloak weiter.

### Docker Compose

`compose.yaml` enthält PostgreSQL (App-Datenbank plus separate `keycloak`-Datenbank) und
Keycloak. Der automatische Start durch Spring Boot ist vorerst aus, weil eine eigene
PostgreSQL-Instanz genutzt wird; einschalten mit `DOCKER_COMPOSE_ENABLED=true`
(dann darf auf Port 5432 nichts anderes laufen).

Keycloak manuell starten:

```bash
docker compose --profile keycloak up -d
```

Der Keycloak-Container hängt vom PostgreSQL-Container aus `compose.yaml` ab. Läuft auf Port 5432
bereits eine andere PostgreSQL-Instanz, `POSTGRES_PORT` auf einen freien Port setzen
(z.&nbsp;B. `POSTGRES_PORT=5433`). Das Init-Skript unter `docker/postgres/init/` legt die
Keycloak-Datenbank nur beim allerersten Start des Volumes an; bei einem bestehenden Volume
muss sie bereits existieren.

Variablen für `compose.yaml` (per Umgebung oder `.env`-Datei):

| Variable                  | Default         |
|---------------------------|-----------------|
| `POSTGRES_PORT`           | `5432`          |
| `POSTGRES_DB`             | `haushaltsbuch` |
| `POSTGRES_USER`           | `haushaltsbuch` |
| `POSTGRES_PASSWORD`       | `haushaltsbuch` |
| `KEYCLOAK_DB`             | `keycloak`      |
| `KEYCLOAK_DB_USER`        | `keycloak`      |
| `KEYCLOAK_DB_PASSWORD`    | `keycloak`      |
| `KEYCLOAK_PORT`           | `8180`          |
| `KEYCLOAK_ADMIN`          | `admin`         |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin`         |

## Keycloak einrichten

Einmalig pro Keycloak-Instanz in der Admin-Konsole (lokal http://localhost:8180, Dev-Zugang
`admin` / `admin`). Alle Schritte außer dem ersten im Realm `haushaltsbuch`, nicht in `master`.

1. **Realm anlegen:** Realm-Auswahl oben links → *Create realm* → Name `haushaltsbuch`.
2. **Login-Einstellungen:** *Realm settings* → Reiter *Login*:
   - *User registration* **aus**: Konten entstehen nur über `POST /api/registrierung`, damit
     zu jedem Konto auch ein Nutzer mit Komponenten in unserer Datenbank existiert.
   - *Login with email* **an**
   - *Duplicate emails* **aus**: Die Registrierung erkennt vergebene E-Mail-Adressen an der
     Antwort 409 von Keycloak.
3. **Passwort-Richtlinie:** *Authentication* → Reiter *Policies* → *Password policy*.
   Mindestlänge nicht unter 8, das Backend prüft selbst nur diese Länge. Strengere Regeln
   (Ziffern, Sonderzeichen usw.) prüft Keycloak; Verstöße gibt die Registrierung als 400 mit
   der Meldung von Keycloak zurück.
4. **Client anlegen:** *Clients* → *Create client*:
   - *Client type* `OpenID Connect`, *Client ID* `haushaltsbuch-backend`
   - *Client authentication* **an** (Confidential Client)
   - *Authentication flow*: *Standard flow* **an**, *Service accounts roles* **an**,
     *Direct access grants* **aus**
   - *Valid redirect URIs*: `http://localhost:8080/login/oauth2/code/keycloak`
     (in prod die echte Basis-URL des Backends)
5. **Rolle für den Service Account:** *Clients* → `haushaltsbuch-backend` → Reiter
   *Service account roles* → *Assign role* → Filter *Filter by clients* → `manage-users` von
   `realm-management` zuweisen. Ohne diese Rolle schlägt die Registrierung mit einem Fehler der
   Admin API fehl. Die Rolle deckt auch das Löschen ab (siehe [Registrierung](#registrierung)).
6. **Client-Secret:** Reiter *Credentials* → *Client Secret* kopieren und als
   `KEYCLOAK_CLIENT_SECRET` setzen.

Benutzer werden mit der E-Mail-Adresse als Benutzername angelegt.

Tipp: Eine fertig eingerichtete Realm-Konfiguration lässt sich mit `kc.sh export --realm haushaltsbuch`
exportieren und beim Start mit `--import-realm` wieder einspielen, statt die Schritte zu wiederholen.

### Für prod zusätzlich

- Keycloak mit `start` statt `start-dev`, mit HTTPS und eigenem Hostnamen betreiben.
- Den Bootstrap-Admin durch ein echtes Admin-Konto ersetzen.
- Redirect-URI des Clients auf die echte Basis-URL des Backends setzen.
- `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` und `KEYCLOAK_ADMIN_URI`
  setzen; im Profil `prod` gibt es keine Defaults.

## Anmeldung und Zugriffsschutz

Mit Profil `keycloak` gibt es zwei Wege der Anmeldung über dieselbe Filterkette:

- **Session (BFF):** Das Backend führt den Login gegen Keycloak serverseitig aus
  (Start über `/oauth2/authorization/keycloak`), die SPA erhält nur ein Session-Cookie.
  Schreibende Anfragen brauchen dann ein CSRF-Token: Das Backend setzt das Cookie `XSRF-TOKEN`,
  die SPA schickt den Wert im Header `X-XSRF-TOKEN` zurück.
- **Bearer-Token:** Anfragen mit einem Access Token von Keycloak (z.&nbsp;B. andere Clients, Tests)
  werden gegen den Realm geprüft.

Antworten unter `/api/**`: **401** ohne Anmeldung (statt einer Weiterleitung zur Login-Seite),
**403** bei fehlender Berechtigung oder fehlendem CSRF-Token.

Öffentlich ohne Anmeldung sind nur `/actuator/health/**`, `/actuator/info`,
`POST /api/registrierung` und im dev-Profil die OpenAPI-Dokumentation.

### Komponenten

Bei der Registrierung wählt der Nutzer Komponenten; sie entscheiden, welche Fachbereiche er
nutzen darf. Die Komponenten liegen in unserer Datenbank (`nutzer_komponente`), nicht als
Rollen in Keycloak. Änderungen greifen so sofort und nicht erst nach einem neuen Login.

| Komponente             | Modul        | API-Pfad              |
|------------------------|--------------|-----------------------|
| `FINANZUEBERSICHT`     | `buchungen`  | `/api/buchungen/**`   |
| `VERTRAGSUEBERSICHT`   | `vertraege`  | `/api/vertraege/**`   |
| `VERSORGUNGSVERTRAEGE` | `versorgung` | `/api/versorgung/**`  |
| `PORTFOLIO`            | `portfolio`  | `/api/portfolio/**`   |

`SecurityConfig` prüft bei jeder Anfrage auf einen dieser Pfade, ob der Nutzer die Komponente
gebucht hat, sonst **403**. **Neue Endpunkte eines Fachmoduls müssen unter dessen API-Pfad
liegen**; ein Endpunkt außerhalb ist für jeden angemeldeten Nutzer erreichbar.

Die SPA fragt die gebuchten Komponenten über `GET /api/nutzer/ich/komponenten` ab und blendet
danach Bereiche ein oder aus. Das ist reine Anzeige, der Schutz liegt im Backend.

Die Nutzer-ID im Code ist immer die Keycloak-ID (Claim `sub`), ermittelt über
`AngemeldeterNutzer.id(...)`. `Authentication#getName()` liefert beim Session-Login den
Benutzernamen (`preferred_username`), nicht die ID.

Keycloak-Rollen werden derzeit nicht ausgewertet. Werden sie später gebraucht (z.&nbsp;B. für einen
Admin-Bereich), steht in `SecurityConfig`, wie sie übernommen werden.

## API

| Endpunkt                          | Zugriff         | Beschreibung                                    |
|-----------------------------------|-----------------|-------------------------------------------------|
| `POST /api/registrierung`         | öffentlich      | Konto in Keycloak und Nutzer anlegen            |
| `GET /api/nutzer/ich/komponenten` | angemeldet      | Gebuchte Komponenten, z.&nbsp;B. `["PORTFOLIO"]`|
| `/oauth2/authorization/keycloak`  | öffentlich      | Startet den Login (Session)                     |
| `/actuator/health`                | öffentlich      | Health Check, inkl. Probes `liveness`/`readiness`|

Die vollständige API-Beschreibung liefert die Swagger UI (`/swagger-ui.html`, nur dev).

### Registrierung

`POST /api/registrierung` (CSRF-Token nötig):

```json
{
  "email": "max@example.org",
  "passwort": "Geheim123!",
  "vorname": "Max",
  "name": "Muster",
  "komponenten": ["FINANZUEBERSICHT", "PORTFOLIO"]
}
```

Antworten: **201** angelegt, **400** ungültige Daten oder Passwort von Keycloak abgelehnt,
**409** E-Mail bereits vergeben. Danach meldet sich der Nutzer über den normalen Login an.

Ablauf: Erst wird das Konto über die Keycloak Admin API angelegt, dann der Nutzer mit seinen
Komponenten gespeichert. Schlägt das Speichern fehl, wird das Konto wieder gelöscht. Schlägt
auch das Löschen fehl, steht im Log *„Konto … konnte nicht gelöscht werden und muss manuell
entfernt werden“*; das Konto dann in Keycloak unter *Users* löschen.

Ein Konto, das direkt in Keycloak angelegt wird, hat keinen Nutzer in unserer Datenbank und
damit keine Komponenten: Login klappt, alle Fachbereiche antworten mit 403.

### Bekannte Einschränkungen

- **Logout:** Es ist kein OIDC-Logout bei Keycloak konfiguriert. `POST /logout` beendet nur die
  Session im Backend; die Session bei Keycloak bleibt bestehen, der nächste Login geht ohne
  Passwortabfrage durch.
- **Kein Ändern der Komponenten:** Komponenten werden nur bei der Registrierung gesetzt.
- **Weiterleitung nach dem Login:** Nach dem Login leitet das Backend auf seine eigene Startseite
  (bzw. die zuvor angefragte Seite) weiter. Läuft die SPA unter einer anderen Adresse, muss das
  angepasst werden.

## Konfiguration

Die Konfiguration liegt ausschließlich in `src/main/resources/application.yml`
(Profile `dev`, `keycloak` und `prod`). Werte kommen aus Umgebungsvariablen; im Profil `dev`
gibt es Defaults für die lokale Entwicklung, im Profil `prod` sind alle Variablen Pflicht.

| Variable                 | Dev-Default                                      |
|--------------------------|--------------------------------------------------|
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/haushaltsbuch` |
| `DB_USERNAME`            | `haushaltsbuch`                                  |
| `DB_PASSWORD`            | `haushaltsbuch`                                  |
| `KEYCLOAK_ISSUER_URI`    | `http://localhost:8180/realms/haushaltsbuch`     |
| `KEYCLOAK_CLIENT_ID`     | `haushaltsbuch-backend`                          |
| `KEYCLOAK_CLIENT_SECRET` | leer                                             |
| `KEYCLOAK_ADMIN_URI`     | `http://localhost:8180/admin/realms/haushaltsbuch` |
| `DOCKER_COMPOSE_ENABLED` | `false`                                          |

Die `KEYCLOAK_*`-Variablen werden nur mit Profil `keycloak` ausgewertet. `KEYCLOAK_ADMIN_URI`
zeigt auf die Admin REST API des Realms, über die die Registrierung Konten anlegt; Login und
Admin API nutzen denselben Client und dasselbe Secret.

Die Keycloak-Endpunkte sind explizit konfiguriert statt über OIDC-Discovery. Dadurch startet
das Backend auch, wenn Keycloak (noch) nicht erreichbar ist.

Produktiv starten mit `SPRING_PROFILES_ACTIVE=prod`. Dort sind Keycloak immer aktiv, der Docker Compose Support
sowie Swagger UI und die OpenAPI-Dokumentation deaktiviert.

### Datenbank

Das Datenbankschema wird ausschließlich über Flyway (`src/main/resources/db/migration`)
gepflegt; Hibernate prüft es nur (`ddl-auto: validate`). Jede neue Entity braucht daher eine
Migration, sonst startet die Anwendung nicht.

Namenskonvention: `V<n>__<modul>_<beschreibung>.sql`, z.&nbsp;B. `V3__buchungen_kategorien.sql`.
Bereits ausgelieferte Migrationen nicht mehr ändern, sondern eine neue anlegen.

Die Tabelle `nutzer` speichert nur die Keycloak-ID und die Komponenten; E-Mail, Name und
Passwort liegen ausschließlich in Keycloak.

## Build und Tests

```bash
./mvnw verify                                     # Build inkl. aller Tests
./mvnw test -Dtest=ModularityTests                # einzelne Testklasse
./mvnw test -Dtest=SecurityConfigTests#healthIsPublic   # einzelne Testmethode
./mvnw spring-boot:build-image -DskipTests        # Container-Image bauen
```

Integrationstests starten PostgreSQL per Testcontainers und benötigen daher eine laufende
Container-Umgebung. Keycloak wird in den Tests nicht gebraucht: Tests mit Profil `keycloak`
simulieren die Anmeldung (`jwt()`, `oidcLogin()`) und ersetzen den Zugriff auf die Admin API
durch Mocks. `ModularityTests` prüft die Modulstruktur mit Spring Modulith.

Zum lokalen Start mit Testcontainers statt Docker Compose:
`TestHaushaltsbuchBackendApplication` (unter `src/test`) ausführen.

Die GitHub Action `.github/workflows/build.yml` führt bei jedem Push und Pull Request
`./mvnw verify` aus und lädt bei Fehlern die Testberichte hoch.

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
├── nutzer       Registrierung, gebuchte Komponenten
└── shared       Security-Konfiguration, gemeinsame Typen (z. B. Komponente)
```

Regeln, die `ModularityTests` prüft:

- Nur das Wurzelpaket eines Moduls ist seine öffentliche API; Unterpakete sind intern.
- Keine zyklischen Abhängigkeiten zwischen Modulen.
- `shared` ist offen (`Type.OPEN`) und darf von allen Modulen genutzt werden, selbst aber
  von keinem Fachmodul abhängen. Braucht `shared` Daten aus einem Modul, definiert es eine
  Schnittstelle, die das Modul implementiert (Beispiel: `KomponentenAbfrage`).

Weitere Dateien:

- `compose.yaml`: PostgreSQL und Keycloak für die lokale Entwicklung
- `docker/postgres/init/`: Init-Skript, das die Keycloak-Datenbank anlegt
- `.github/workflows/build.yml`: Build und Tests in GitHub Actions
