# Betrieb

Lokale Umgebung mit Docker Compose, Betrieb per Compose aus den Images, Einrichtung von Keycloak,
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
Anwendung aus den veröffentlichten Images: PostgreSQL, Keycloak, Backend (Profil `prod`) und Frontend.

Für die eigene Nutzung im Heimnetz gedacht, nicht für das offene Internet: Keycloak läuft ohne HTTPS,
die Session-Cookies gehen unverschlüsselt durchs lokale Netz. TLS, Reverse Proxy und Keycloak für prod
folgen mit v1.0.0 (#7, #17).

1. Bei `ghcr.io` anmelden, die Images sind privat: `podman login ghcr.io -u <GitHub-Name>`, als Passwort ein
   klassischer Token mit `read:packages` (GitHub: Settings → Developer settings → Personal access tokens →
   Tokens (classic)). Fine-grained Tokens haben kein Recht für Packages.
2. Paket entpacken und darin `.env` aus `.env.beispiel` anlegen. Alle Werte sind Pflicht, außer
   `HAUSHALTSBUCH_PORT` (Standard `8080`); fehlt einer, bricht Compose mit einer Meldung ab. Passwörter und
   Secret zufällig wählen, z.&nbsp;B. `openssl rand -base64 32`. `HAUSHALTSBUCH_STAGE` ist `PROD` für echte
   Daten oder `DEV` für eine Testinstanz (die Oberfläche zeigt dann einen Streifen).
3. `instanz.json` mit den Angaben des Betreibers für Impressum und Datenschutz neben `compose.yaml` legen
   (Format im README des Frontends, Abschnitt Container). Nie die Beispieldatei aus dem Frontend-Repo
   verwenden; ohne Datei zeigen beide Seiten nur einen Hinweis.
4. Starten: `podman compose up -d` (oder `docker compose up -d`). Die Anwendung ist danach unter
   `http://<Rechner>:8080` erreichbar, auch von anderen Geräten im Heimnetz.

Erreichbar ist nur das Frontend; Backend, Keycloak und PostgreSQL bleiben im internen Compose-Netz. Die
Keycloak-Admin-Oberfläche ist nicht veröffentlicht, ein Konto sperren oder löschen geht über
`podman compose exec keycloak /opt/keycloak/bin/kcadm.sh …` mit dem Bootstrap-Admin `admin` und
`KEYCLOAK_ADMIN_PASSWORD`. `KEYCLOAK_CLIENT_SECRET` wirkt nur beim ersten Start (Realm-Import); die Daten
liegen im Volume `postgres-data`. Ein Update heißt: neues Paket, `.env` und `instanz.json` übernehmen,
`podman compose up -d`.

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
- **Einzeln gestarteter Container** (ohne `compose.yaml`): Verzeichnis einbinden und Import
  aktivieren, z.&nbsp;B.

  ```bash
  podman run -d --name keycloak -p 8180:8080 \
    -v ./docker/keycloak/import:/opt/keycloak/data/import:ro,Z \
    -e KEYCLOAK_CLIENT_SECRET=dev-secret \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
    quay.io/keycloak/keycloak:26.8.1 start-dev --import-realm
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
6. **Client-Secret:** Reiter *Credentials* → *Client Secret* kopieren und als
   `KEYCLOAK_CLIENT_SECRET` setzen (der Import setzt es aus derselben Variablen).
7. **Sperre nach Fehlversuchen:** *Realm settings* → Reiter *Security defenses* →
   *Brute force detection* einschalten. Das Login-Formular der SPA prüft Passwörter über das
   Backend; ohne Sperre ließen sie sich dort beliebig durchprobieren. Der Keycloak-Standard
   (30 Fehlversuche) ist großzügig, vor prod niedriger ansetzen (z.&nbsp;B. 5–10).
8. **Keine Pflichtschritte:** Unter *Authentication* → *Required actions* keine Aktion als
   *Default action* aktivieren und Konten nicht mit temporärem Passwort anlegen. Die Seiten, auf
   denen Keycloak solche Schritte abfragt, werden nie angezeigt; ein Konto mit offenem
   Pflichtschritt kann sich nicht anmelden (Antwort 403, `code` `ANMELDUNG_NICHT_EINGERICHTET`).

Benutzer werden mit der E-Mail-Adresse als Benutzername angelegt.

Außerdem setzt der Import *Require SSL* auf `none` (*Realm settings* → *General*): Hinter dem
Port-Forwarding von Podman gelten Anfragen nicht als lokal, mit dem Keycloak-Standard `external`
lehnt Keycloak sie daher ohne HTTPS ab.

### Konten und Registrierung

Ablauf: Erst wird das Konto über die Keycloak Admin API angelegt, dann werden in einer
Transaktion der Nutzer und sein Haushalt mit den Komponenten gespeichert. Schlägt das Speichern fehl, wird das Konto wieder gelöscht. Schlägt
auch das Löschen fehl, steht im Log *„Konto … konnte nicht gelöscht werden und muss manuell
entfernt werden“*; das Konto dann in Keycloak unter *Users* löschen.

Ein Konto, das direkt in Keycloak angelegt wird, hat keinen Nutzer in unserer Datenbank und
damit keinen Haushalt und keine Komponenten: Login klappt, alle Fachbereiche antworten mit 403.

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

Produktiv starten mit `SPRING_PROFILES_ACTIVE=prod`. Dort sind der Docker Compose Support
sowie Swagger UI und die OpenAPI-Dokumentation deaktiviert.

## Systemstatus

Format für die SPA siehe [API](api.md#systemstatus).

`keycloak` prüft das OIDC-Discovery-Dokument des Realms (Timeout 2&nbsp;s) und merkt sich das
Ergebnis 10&nbsp;s, damit der öffentliche Endpunkt Keycloak nicht mit Anfragen belastet. Da `keycloak` auch in
`/actuator/health` eingeht, sollten Container-Healthchecks die Probes `liveness`/`readiness`
verwenden, sonst wird das Backend bei einem Keycloak-Ausfall neu gestartet.
