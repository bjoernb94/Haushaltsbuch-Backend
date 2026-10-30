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
3. Anmeldung testen, z.&nbsp;B. mit curl (CSRF-Token aus dem Cookie holen, dann anmelden):

   ```bash
   curl -s -c /tmp/kc.txt http://localhost:8080/actuator/health >/dev/null
   XSRF=$(awk '/XSRF-TOKEN/{print $7}' /tmp/kc.txt)
   curl -i -b /tmp/kc.txt -c /tmp/kc.txt -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
     -d '{"email":"max@example.org","passwort":"Geheim123!"}' http://localhost:8080/api/anmeldung
   curl -b /tmp/kc.txt http://localhost:8080/api/nutzer/ich/komponenten
   ```

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
   - *Authentication flow*: *Direct access grants* **an** (Anmeldung mit E-Mail und Passwort
     über das Backend), *Service accounts roles* **an** (Registrierung), *Standard flow* **aus**
     (es gibt keine Weiterleitung zu Keycloak, Redirect-URIs werden daher nicht gebraucht)
5. **Rolle für den Service Account:** *Clients* → `haushaltsbuch-backend` → Reiter
   *Service account roles* → *Assign role* → Filter *Filter by clients* → `manage-users` von
   `realm-management` zuweisen. Ohne diese Rolle schlägt die Registrierung mit einem Fehler der
   Admin API fehl. Die Rolle deckt auch das Löschen ab (siehe [Registrierung](#registrierung)).
6. **Client-Secret:** Reiter *Credentials* → *Client Secret* kopieren und als
   `KEYCLOAK_CLIENT_SECRET` setzen.
7. **Sperre nach Fehlversuchen:** *Realm settings* → Reiter *Security defenses* →
   *Brute force detection* einschalten. Das Login-Formular der SPA prüft Passwörter über das
   Backend; ohne Sperre ließen sie sich dort beliebig durchprobieren. Der Keycloak-Standard
   (30 Fehlversuche) ist großzügig, vor prod niedriger ansetzen (z.&nbsp;B. 5–10).
8. **Keine Pflichtschritte:** Unter *Authentication* → *Required actions* keine Aktion als
   *Default action* aktivieren und Konten nicht mit temporärem Passwort anlegen. Die Seiten, auf
   denen Keycloak solche Schritte abfragt, werden nie angezeigt; ein Konto mit offenem
   Pflichtschritt kann sich nicht anmelden (Antwort 403, `grund` `NICHT_EINGERICHTET`).

Benutzer werden mit der E-Mail-Adresse als Benutzername angelegt.

Tipp: Eine fertig eingerichtete Realm-Konfiguration lässt sich mit `kc.sh export --realm haushaltsbuch`
exportieren und beim Start mit `--import-realm` wieder einspielen, statt die Schritte zu wiederholen.

### Für prod zusätzlich

- Keycloak mit `start` statt `start-dev`, mit HTTPS und eigenem Hostnamen betreiben.
- Den Bootstrap-Admin durch ein echtes Admin-Konto ersetzen.
- Backend und Keycloak nur über HTTPS verbinden: Das Backend schickt Passwörter an Keycloak.
- `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` und `KEYCLOAK_ADMIN_URI`
  setzen; im Profil `prod` gibt es keine Defaults.

## Anmeldung und Zugriffsschutz

Mit Profil `keycloak` gibt es zwei Wege der Anmeldung über dieselbe Filterkette:

- **Session:** Die SPA hat ein eigenes Login-Formular und schickt E-Mail und Passwort an
  `POST /api/anmeldung`. Das Backend holt damit die Tokens direkt bei Keycloak (Password Grant,
  in Keycloak *Direct access grants*) und legt die Session an; die SPA erhält nur das
  Session-Cookie. Es gibt keine Weiterleitung zu Keycloak.
- **Bearer-Token:** Anfragen mit einem Access Token von Keycloak (z.&nbsp;B. andere Clients, Tests)
  werden gegen den Realm geprüft.

### Ablauf für die SPA

- **CSRF:** Das Backend setzt das Cookie `XSRF-TOKEN` (bei jeder Antwort, auch bei 401). Jede
  schreibende Anfrage, auch Anmeldung und Registrierung, schickt den Wert im Header
  `X-XSRF-TOKEN` zurück. Nach der Anmeldung wird das Token erneuert; Angular liest es dafür
  ohnehin vor jeder Anfrage neu aus dem Cookie.
- **Beim Start:** `GET /api/nutzer/ich/komponenten`. **401** → Login-Seite zeigen, **200** →
  angemeldet, Bereiche nach der Liste einblenden.
- **Anmeldung:** `POST /api/anmeldung` mit `{"email": "...", "passwort": "..."}`.
  - **204**: angemeldet, Session-Cookie gesetzt
  - **401**, `grund` `UNGUELTIG`: E-Mail-Adresse oder Passwort falsch
  - **403**, `grund` `GESPERRT`: Konto deaktiviert oder nach Fehlversuchen vorübergehend gesperrt
  - **403**, `grund` `NICHT_EINGERICHTET`: in Keycloak stehen Pflichtschritte aus
  - **400**: Felder fehlen
- **Registrierung:** Nach **201** ist der Nutzer direkt angemeldet. Scheitert nur die automatische
  Anmeldung, bleibt es bei 201 und die SPA zeigt die Login-Seite (erkennbar an der 401 beim
  nächsten Aufruf).
- **Abmeldung:** `POST /api/abmeldung` → **204**. Beendet die Session im Backend und die Sitzung
  bei Keycloak.

Die SPA muss das Backend über denselben Origin erreichen, lokal über den Angular-Dev-Proxy
(`/api` auf `http://localhost:8080`). Nur dann schickt der Browser Session- und XSRF-Cookie mit;
CORS ist im Backend nicht konfiguriert.

### Sitzung

Die Session im Backend speichert das Refresh Token von Keycloak. Läuft das Access Token ab
(Standard 5 Minuten), erneuert das Backend die Sitzung beim nächsten Request bei Keycloak.
Lehnt Keycloak das ab (Sitzung abgelaufen oder in Keycloak beendet, Konto deaktiviert), wird
die Session beendet und der Request erhält 401. Ein in Keycloak gesperrter Nutzer ist also
spätestens nach Ablauf des Access Tokens abgemeldet. Ist Keycloak nicht erreichbar, bleibt die
Session bestehen und die Erneuerung wird beim nächsten Request wiederholt.

Wie lange eine Anmeldung ohne Aktivität hält, bestimmt das Kürzere aus Session-Timeout des
Backends (Standard 30 Minuten) und *SSO Session Idle* im Realm (Standard 30 Minuten).

### Antworten und öffentliche Endpunkte

Antworten unter `/api/**`: **401** ohne Anmeldung, **403** bei fehlender Berechtigung oder
fehlendem CSRF-Token.

Öffentlich ohne Anmeldung sind nur `/actuator/health/**`, `/actuator/info`,
`POST /api/registrierung`, `POST /api/anmeldung` und im dev-Profil die OpenAPI-Dokumentation.

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

Die Nutzer-ID im Code ist immer die Keycloak-ID (Claim `sub`), als `UUID` ermittelt über
`AngemeldeterNutzer.id(...)`.

Keycloak-Rollen werden derzeit nicht ausgewertet. Werden sie später gebraucht (z.&nbsp;B. für einen
Admin-Bereich), steht in `SecurityConfig`, wie sie übernommen werden.

## API

| Endpunkt                          | Zugriff         | Beschreibung                                    |
|-----------------------------------|-----------------|-------------------------------------------------|
| `POST /api/registrierung`         | öffentlich      | Konto in Keycloak und Nutzer anlegen            |
| `GET /api/nutzer/ich`             | angemeldet      | Profil: `vorname`, `name`, `email`              |
| `GET /api/nutzer/ich/komponenten` | angemeldet      | Gebuchte Komponenten, z.&nbsp;B. `["PORTFOLIO"]`|
| `POST /api/anmeldung`             | öffentlich      | Anmeldung mit E-Mail und Passwort (Session)     |
| `POST /api/abmeldung`             | angemeldet      | Abmeldung, auch bei Keycloak                    |
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
**409** E-Mail bereits vergeben. Nach 201 ist der Nutzer direkt angemeldet.

Ablauf: Erst wird das Konto über die Keycloak Admin API angelegt, dann der Nutzer mit seinen
Komponenten gespeichert. Schlägt das Speichern fehl, wird das Konto wieder gelöscht. Schlägt
auch das Löschen fehl, steht im Log *„Konto … konnte nicht gelöscht werden und muss manuell
entfernt werden“*; das Konto dann in Keycloak unter *Users* löschen.

Ein Konto, das direkt in Keycloak angelegt wird, hat keinen Nutzer in unserer Datenbank und
damit keine Komponenten: Login klappt, alle Fachbereiche antworten mit 403.

### Spätere Aufgaben

- **Passwort vergessen:** Fehlt noch. Da die Keycloak-Seiten nicht genutzt werden, braucht es
  einen eigenen Ablauf (Link per E-Mail, eigenes Formular, neues Passwort über die Admin API)
  und dafür einen Mailserver.
- **Sofortige Abmeldung bei Ende der Keycloak-Sitzung:** Bisher erkennt das Backend eine in
  Keycloak beendete Sitzung erst bei der nächsten Erneuerung (spätestens nach Ablauf des
  Access Tokens). Für sofortige Wirkung den Back-Channel-Logout von Keycloak selbst umsetzen:
  einen Endpunkt, den Keycloak mit einem Logout-Token aufruft (*Backchannel logout URL* am
  Client), und die zugehörige Session beenden. Die fertige Lösung von Spring
  (`oidcLogout`) setzt den hier nicht genutzten `oauth2Login` voraus.
- **Sessions über Neustarts hinweg:** Sessions liegen im Speicher des Backends. Ein Neustart
  meldet alle Nutzer ab, mehrere Instanzen teilen sich keine Sessions. Bei Bedarf Spring Session
  (z.&nbsp;B. mit JDBC) einsetzen.
- **Zusätzliche Begrenzung der Anmeldeversuche:** Die Sperre in Keycloak gilt je Konto. Gegen
  das Durchprobieren vieler Konten hilft eine Begrenzung je IP-Adresse vor `/api/anmeldung`
  (Reverse Proxy oder im Backend).
- **Bearer-Token:** Für Anfragen mit Bearer-Token gibt es keinen Logout im Backend; ein Token gilt
  bis zu seinem Ablauf. Die *Access Token Lifespan* im Realm (Standard 5 Minuten) kurz halten und
  vor prod prüfen.
- **Profilbereich:** Name und E-Mail sollen später in einem eigenen Profilbereich der App
  geändert werden, nicht direkt in Keycloak. `GET /api/nutzer/ich` liest sie aus dem Token der
  Anmeldung; nach einer Änderung muss das Backend sie per Admin API in Keycloak schreiben und die
  Angaben in der Session aktualisieren, sonst zeigt `/ich` bis zur nächsten Anmeldung die alten.
- **Komponenten ändern:** Komponenten werden bisher nur bei der Registrierung gesetzt; ein
  Endpunkt zum Hinzu- oder Abbuchen fehlt.

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
zeigt auf die Admin REST API des Realms, über die die Registrierung Konten anlegt; Anmeldung und
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
