# Betrieb

Lokale Umgebung mit Docker Compose, Betrieb per Compose aus den Images und Update, Einrichtung von Keycloak,
Konfiguration, prod und Systemstatus.
Zum Starten des Backends siehe [README](../README.md#starten).

## Docker Compose

`compose.yaml` enthält die lokale Entwicklungsumgebung: PostgreSQL (App-Datenbank `haushaltsbuch` plus
separate `keycloak`-Datenbank) und Keycloak mit Realm-Import. Sie startet getrennt vom Backend, über die
Run Configurations „Umgebung starten“ und „Umgebung stoppen“ oder von Hand:

```bash
podman compose up -d   # oder docker compose up -d
podman compose stop    # anhalten, die Daten bleiben im Volume postgres-data
```

So lässt sich das Backend neu starten, ohne dass die Datenbank neu startet. Spring Boot kann die Umgebung
auch selbst mitstarten (`DOCKER_COMPOSE_ENABLED=true`), das ist aber aus.

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
| `KEYCLOAK_CLIENT_SECRET`  | `dev-secret`    |

## Betrieb per Compose

Jedes Release hat ein Paket `haushaltsbuch-<version>.tar.gz` (erzeugt von `scripts/betrieb-paket.sh`) mit
`compose.yaml`, `.env.beispiel`, dem Realm-Import und dem Init-Skript der Datenbank. Damit startet die ganze
Anwendung aus den veröffentlichten, öffentlichen Images (ohne Anmeldung bei `ghcr.io`): PostgreSQL, Keycloak, Backend (Profil `prod`) und Frontend.

Für die eigene Nutzung im Heimnetz gedacht, nicht für das offene Internet: Keycloak läuft ohne HTTPS,
die Session-Cookies gehen unverschlüsselt durchs lokale Netz. TLS, Reverse Proxy und Keycloak für prod
folgen mit v1.0.0 (#7, #17).

1. Paket entpacken und darin `.env` aus `.env.beispiel` anlegen. Alle Werte sind Pflicht, außer
   `HAUSHALTSBUCH_PORT` (Standard `8080`); fehlt einer, bricht Compose mit einer Meldung ab. Passwörter und
   Secret zufällig wählen, z.&nbsp;B. `openssl rand -base64 32`. `HAUSHALTSBUCH_STAGE` ist `PROD` für echte
   Daten oder `DEV` für eine Testinstanz (die Oberfläche zeigt dann einen Streifen).
2. `instanz.json` mit den Angaben des Betreibers für Impressum und Datenschutz neben `compose.yaml` legen
   (Format unter
   [Angaben des Betreibers](https://github.com/bjoernb94/Haushaltsbuch-Frontend/blob/main/docs/betrieb.md#angaben-des-betreibers)
   im Frontend). Nie die Beispieldatei aus dem Frontend-Repo verwenden; ohne Datei zeigen beide Seiten nur
   einen Hinweis.
3. Starten: `podman compose up -d` (oder `docker compose up -d`). Die Anwendung ist danach unter
   `http://<Rechner>:8080` erreichbar, auch von anderen Geräten im Heimnetz.

Erreichbar ist nur das Frontend; Backend, Keycloak und PostgreSQL bleiben im internen Compose-Netz. Die
Keycloak-Admin-Oberfläche ist nicht veröffentlicht, ein Konto sperren oder löschen geht über
`podman compose exec keycloak /opt/keycloak/bin/kcadm.sh …` mit dem Bootstrap-Admin `admin` und
`KEYCLOAK_ADMIN_PASSWORD`. `KEYCLOAK_CLIENT_SECRET` wirkt nur beim ersten Start (Realm-Import); die Daten
liegen im Volume `postgres-data`.

### Update

Gilt für jedes Release. Was ein Release darüber hinaus von Hand braucht (z.&nbsp;B. eine Einstellung in Keycloak),
steht in seinen Release Notes; wer Versionen überspringt, führt die Schritte aller übersprungenen Releases aus.

1. **Release Notes lesen**, auch die der übersprungenen Releases.
2. **Datenbank sichern**, im Verzeichnis des bisherigen Pakets:
   `podman compose exec postgres pg_dumpall -U haushaltsbuch > sicherung.sql`. Das sichert die App-Datenbank und
   die von Keycloak. Beide migrieren ihr Schema beim Start (das Backend mit Flyway, Keycloak selbst); zurück auf die
   alte Version geht danach nur über diese Sicherung.
3. **Neues Paket entpacken** und `.env` und `instanz.json` aus dem bisherigen Verzeichnis hineinkopieren. Ein
   eigenes Verzeichnis je Version ist kein Problem: `compose.yaml` legt den Projektnamen `haushaltsbuch` fest,
   Compose nutzt also dasselbe Volume `postgres-data`.
4. **`.env` mit der neuen `.env.beispiel` vergleichen** und neue Variablen ergänzen. Fehlt eine Pflichtangabe,
   bricht Compose mit einer Meldung ab; neue optionale fallen sonst nicht auf.
5. **Images holen und starten:** `podman compose pull`, dann `podman compose up -d` (oder `docker compose …`).
   Compose startet nur die Container mit neuem Image neu; mit `pull` vorher ist die Anwendung kürzer weg. Backend
   und Frontend gehören zusammen, das Paket startet beide in derselben Version.
6. **Keycloak neu starten, wenn PostgreSQL neu gestartet ist** (neues Image), Keycloak aber nicht:
   `podman compose restart keycloak`. Keycloak hält sonst die beendeten Verbindungen im Pool, und die ersten
   Anmeldungen und Registrierungen scheitern mit 500, bis alle einmal verworfen sind. Das Backend prüft seine
   Verbindungen vor der Benutzung und braucht das nicht. Dasselbe gilt für die lokale Umgebung aus `compose.yaml`.
7. **Prüfen:** `curl http://<Rechner>:8080/actuator/info` zeigt die neue Version, die Anmeldeseite den
   [Systemstatus](#systemstatus). Startet das Backend nicht, steht der Grund in `podman compose logs backend`, meist
   eine Migration, die bewusst abbricht, statt Daten still zu korrigieren.

Nur bei neuen Instanzen wirken der Realm-Import (`keycloak/import/`, auch `KEYCLOAK_CLIENT_SECRET`) und das
Init-Skript der Datenbank (`postgres/init/`). Ändert ein Release sie, braucht eine bestehende Instanz einen Schritt
von Hand, und der steht in den Release Notes.

## Keycloak einrichten

Der Realm `haushaltsbuch` liegt versioniert unter `docker/keycloak/import/haushaltsbuch-realm.json`
und wird beim Start mit `--import-realm` eingespielt (in `compose.yaml` bereits gesetzt). Der Import
greift nur, wenn es den Realm noch nicht gibt; ein bestehender Realm wird nicht verändert.
Änderungen an der Datei wirken daher nur auf neue Keycloak-Instanzen, in bestehenden muss man sie
von Hand nachziehen.

- **Client-Secret:** steht nicht in der Datei, sondern wird beim Import aus der Umgebungsvariablen
  `KEYCLOAK_CLIENT_SECRET` des Keycloak-Containers gelesen (Default `dev-secret`, passend zum
  Default des Backends in dev).
- **Nutzer:** enthält der Import keine, nur den Service Account des Clients.
- **Client-Scopes:** Der Import setzt am Client `haushaltsbuch-backend` beide Listen, `defaultClientScopes` (die
  Standardliste von Keycloak) und `optionalClientScopes` (die Standardliste plus `kc-admin-api`). Eine angegebene
  Liste ersetzt die Standardliste; fehlte `defaultClientScopes`, bekäme der Client gar keine Default-Scopes, und den
  Anmelde-Tokens fehlten Name und E-Mail.
- **Einzeln gestarteter Container** (ohne `compose.yaml`): Verzeichnis einbinden und Import
  aktivieren, z.&nbsp;B.

  ```bash
  podman run -d --name keycloak -p 8180:8080 \
    -v ./docker/keycloak/import:/opt/keycloak/data/import:ro,Z \
    -e KEYCLOAK_CLIENT_SECRET=dev-secret \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
    quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm
  ```

  (`:Z` setzt das SELinux-Label, z.&nbsp;B. unter Fedora; ohne Angabe von `KC_DB*` speichert
  Keycloak in einer internen Datenbank im Container.)

Die folgenden Schritte beschreiben, was der Import einrichtet. Sie dienen als Referenz, z.&nbsp;B.
um eine bestehende Instanz von Hand anzupassen (Admin-Konsole lokal http://localhost:8180,
Dev-Zugang `admin` / `admin`, alle Schritte außer dem ersten im Realm `haushaltsbuch`):

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
   Admin API fehl. Die Rolle deckt auch das Löschen ab (siehe [Konten und Registrierung](#konten-und-registrierung)).
6. **Client-Scope für die Admin API:** *Clients* → `haushaltsbuch-backend` → Reiter *Client scopes* →
   *Add client scope* → `kc-admin-api` als **Optional** hinzufügen. Ab Keycloak 26.8.2 lehnt die Admin API Tokens
   ohne diesen Scope mit 401 ab. Das Backend fordert ihn nur für den Service Account an (`scope: kc-admin-api` an der
   Registrierung `keycloak-admin`); als *Default* stünde die Admin-API-Audience auch in jedem Anmelde-Token der
   Nutzer. Den Scope selbst legt Keycloak an, beim Import wie beim Upgrade eines bestehenden Realms.
7. **Client-Secret:** Reiter *Credentials* → *Client Secret* kopieren und als
   `KEYCLOAK_CLIENT_SECRET` setzen (der Import setzt es aus derselben Variablen).
8. **Sperre nach Fehlversuchen:** *Realm settings* → Reiter *Security defenses* →
   *Brute force detection* einschalten. Das Login-Formular der SPA prüft Passwörter über das
   Backend; ohne Sperre ließen sie sich dort beliebig durchprobieren. Der Keycloak-Standard
   (30 Fehlversuche) ist großzügig, vor prod niedriger ansetzen (z.&nbsp;B. 5–10).
9. **Keine Pflichtschritte:** Unter *Authentication* → *Required actions* keine Aktion als
   *Default action* aktivieren und Konten nicht mit temporärem Passwort anlegen. Die Seiten, auf
   denen Keycloak solche Schritte abfragt, werden nie angezeigt; ein Konto mit offenem
   Pflichtschritt kann sich nicht anmelden (Antwort 403, `code` `ANMELDUNG_NICHT_EINGERICHTET`).

Benutzer werden mit der E-Mail-Adresse als Benutzername angelegt.

Außerdem setzt der Import *Require SSL* auf `none` (*Realm settings* → *General*): Hinter dem
Port-Forwarding von Podman gelten Anfragen nicht als lokal, mit dem Keycloak-Standard `external`
lehnt Keycloak sie daher ohne HTTPS ab.

### Scope `kc-admin-api` nachtragen

Für eine bestehende Instanz nach dem Update von v0.1.0 auf Keycloak 26.8.2 (Schritt 6 oben, ohne Admin-Konsole):
Ohne den Scope scheitert jede Registrierung (500, im Log des Backends `invalid_scope`), Anmeldungen gehen weiter. Im Verzeichnis
des Betriebspakets; `KEYCLOAK_ADMIN_PASSWORD` steht nur in der `.env`, deshalb zuerst einlesen. In der lokalen
Umgebung im Repo ohne `.env`, das Passwort ist dort `admin`.

```bash
set -a; . ./.env; set +a
K="podman compose exec keycloak /opt/keycloak/bin/kcadm.sh"
$K config credentials --server http://localhost:8080 --realm master --user admin --password "$KEYCLOAK_ADMIN_PASSWORD"
CID=$($K get clients -r haushaltsbuch -q clientId=haushaltsbuch-backend --fields id --format csv --noquotes)
SID=$($K get client-scopes -r haushaltsbuch --fields id,name --format csv --noquotes | grep ',kc-admin-api$' | cut -d, -f1)
$K update clients/$CID/optional-client-scopes/$SID -r haushaltsbuch
$K get clients/$CID/optional-client-scopes -r haushaltsbuch --fields name --format csv --noquotes
```

Die letzte Zeile muss `kc-admin-api` enthalten. Danach klappt die Registrierung wieder; ein Neustart ist nicht
nötig.

### Konten und Registrierung

Ablauf: Erst wird das Konto über die Keycloak Admin API angelegt, dann werden in einer
Transaktion der Nutzer und sein Haushalt mit den Komponenten gespeichert. Schlägt das Speichern fehl, wird das Konto wieder gelöscht. Schlägt
auch das Löschen fehl, steht im Log *„Konto … konnte nicht gelöscht werden und muss manuell
entfernt werden“*; das Konto dann in Keycloak unter *Users* löschen.

Antwortet Keycloak beim Anlegen nicht rechtzeitig (503 `KEYCLOAK_NICHT_ERREICHBAR`), kann das Konto
trotzdem entstanden sein, ohne dass das Backend davon weiß. Eine neue Registrierung scheitert dann mit
`EMAIL_BEREITS_VERGEBEN`.

Ein Konto, das nur in Keycloak existiert (nach den Fällen oben oder direkt in Keycloak angelegt), hat
keinen Nutzer und keinen Haushalt in unserer Datenbank. Die Anmeldung weist es nach der Passwortprüfung
mit 403 `KONTO_UNVOLLSTAENDIG` ab und beendet die Sitzung bei Keycloak wieder; im Log steht
*„Konto `<Keycloak-ID>` hat keinen Haushalt, Anmeldung abgewiesen.“* Meldet sich der Nutzer beim
Support: Das Konto in Keycloak unter *Users* suchen (über die E-Mail-Adresse des Nutzers oder die ID aus
dem Log) und löschen. Danach kann er sich neu registrieren. Konten deshalb nicht direkt in Keycloak
anlegen, sondern über die Registrierung.

### Für prod zusätzlich

- Keycloak mit `start` statt `start-dev`, mit HTTPS und eigenem Hostnamen betreiben.
- *Require SSL* im Realm wieder auf `external` oder `all` setzen (der Import setzt `none`).
- `KEYCLOAK_CLIENT_SECRET` beim Import auf ein eigenes, zufälliges Secret setzen.
- Den Bootstrap-Admin durch ein echtes Admin-Konto ersetzen.
- Backend und Keycloak nur über HTTPS verbinden: Das Backend schickt Passwörter an Keycloak.
- `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` und `KEYCLOAK_ADMIN_URI`
  setzen; im Profil `prod` gibt es keine Defaults.

## Konfiguration

Die Konfiguration liegt ausschließlich in `src/main/resources/application.yml`
(Profile `dev` und `prod`). Werte kommen aus Umgebungsvariablen; im Profil `dev`
gibt es Defaults für die lokale Entwicklung, im Profil `prod` sind alle Variablen Pflicht.

| Variable                 | Dev-Default                                      |
|--------------------------|--------------------------------------------------|
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/haushaltsbuch` |
| `DB_USERNAME`            | `haushaltsbuch`                                  |
| `DB_PASSWORD`            | `haushaltsbuch`                                  |
| `KEYCLOAK_ISSUER_URI`    | `http://localhost:8180/realms/haushaltsbuch`     |
| `KEYCLOAK_CLIENT_ID`     | `haushaltsbuch-backend`                          |
| `KEYCLOAK_CLIENT_SECRET` | `dev-secret` (passend zum Realm-Import)          |
| `KEYCLOAK_ADMIN_URI`     | `http://localhost:8180/admin/realms/haushaltsbuch` |
| `HAUSHALTSBUCH_STAGE`    | `DEV`                                            |
| `DOCKER_COMPOSE_ENABLED` | `false`                                          |

`KEYCLOAK_ADMIN_URI`
zeigt auf die Admin REST API des Realms, über die die Registrierung Konten anlegt; Anmeldung und
Admin API nutzen denselben Client und dasselbe Secret.

`HAUSHALTSBUCH_STAGE` (`DEV` oder `PROD`) nennt die Umgebung der Instanz, die SPA zeigt sie an
(siehe [API](api.md#version-und-umgebung)). Sie hängt nicht am Spring-Profil. Fehlt sie im Profil
`prod` oder hat einen anderen Wert, startet das Backend nicht.

Die Keycloak-Endpunkte sind explizit konfiguriert statt über OIDC-Discovery. Dadurch startet
das Backend auch, wenn Keycloak (noch) nicht erreichbar ist.

Aufrufe an Keycloak (Token-Endpunkt, Schlüssel des Realms, Admin API samt Token des Service Accounts)
brechen nach `spring.http.clients.connect-timeout` (2&nbsp;s) bzw. `spring.http.clients.read-timeout`
(5&nbsp;s) ab. Anmeldung und Registrierung antworten dann mit 503 `KEYCLOAK_NICHT_ERREICHBAR`, im Log
steht *„Keycloak nicht erreichbar bei …“* mit der Ursache. Ändern lassen sich die Werte über
`SPRING_HTTP_CLIENTS_CONNECT_TIMEOUT` und `SPRING_HTTP_CLIENTS_READ_TIMEOUT` (z.&nbsp;B. `10s`). Der
Health-Check hat eigene 2&nbsp;s (siehe [Systemstatus](#systemstatus)).

Produktiv starten mit `SPRING_PROFILES_ACTIVE=prod`. Dort sind der Docker Compose Support
sowie Swagger UI und die OpenAPI-Dokumentation deaktiviert.

## Systemstatus

Format für die SPA siehe [API](api.md#systemstatus).

`keycloak` prüft das OIDC-Discovery-Dokument des Realms (Timeout 2&nbsp;s) und merkt sich das
Ergebnis 10&nbsp;s, damit der öffentliche Endpunkt Keycloak nicht mit Anfragen belastet. Da `keycloak` auch in
`/actuator/health` eingeht, sollten Container-Healthchecks die Probes `liveness`/`readiness`
verwenden, sonst wird das Backend bei einem Keycloak-Ausfall neu gestartet.
