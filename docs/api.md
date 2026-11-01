# API für die SPA

Vertrag zwischen Backend und SPA: Anmeldung, Endpunkte, Fehlerantworten und Datenformate. Wie
man Endpunkte im Backend baut, steht in [Entwicklung](entwicklung.md).

## Endpunkte

| Endpunkt                          | Zugriff         | Beschreibung                                    |
|-----------------------------------|-----------------|-------------------------------------------------|
| `POST /api/registrierung`         | öffentlich      | Konto in Keycloak und Nutzer anlegen            |
| `GET /api/nutzer/ich`             | angemeldet      | Profil: `vorname`, `name`, `email`              |
| `GET /api/nutzer/ich/komponenten` | angemeldet      | Gebuchte Komponenten, z.&nbsp;B. `["PORTFOLIO"]`|
| `POST /api/anmeldung`             | öffentlich      | Anmeldung mit E-Mail und Passwort (Session)     |
| `POST /api/abmeldung`             | angemeldet      | Abmeldung, auch bei Keycloak                    |
| `/actuator/health`                | öffentlich      | Health Check, inkl. Probes `liveness`/`readiness`|
| `/actuator/health/systemstatus`   | öffentlich      | Status für die Login-Seite: `db`, `keycloak`     |

Die vollständige API-Beschreibung (OpenAPI 3.1) liegt als [`openapi.json`](openapi.json) im Repo,
z.&nbsp;B. um daraus Typen für die SPA zu erzeugen; sie wird bei jedem Build neu erzeugt. Zur Laufzeit
zeigt sie die Swagger UI (`/swagger-ui.html`, nur dev). Abläufe, Fehlercodes und Datenformate stehen
nur hier.

### Systemstatus

Für die Login-Seite: `GET /actuator/health/systemstatus` liefert nur `UP`/`DOWN` je Komponente,
ohne Details; ist eine Komponente `DOWN`, antwortet der Endpunkt mit 503:

```json
{ "status": "UP", "components": { "db": { "status": "UP" }, "keycloak": { "status": "UP" } } }
```

Wie die Prüfung von `keycloak` arbeitet und was für Container-Healthchecks gilt, steht unter
[Betrieb](betrieb.md#systemstatus).

## Anmeldung und Zugriffsschutz

Angemeldet wird nur per Session: Die SPA hat ein eigenes Login-Formular und schickt E-Mail und
Passwort an `POST /api/anmeldung`. Das Backend holt damit die Tokens direkt bei Keycloak (Password
Grant, in Keycloak *Direct access grants*) und legt die Session an; die SPA erhält nur das
Session-Cookie. Es gibt keine Weiterleitung zu Keycloak. Bearer-Tokens nimmt das Backend nicht an
([Entscheidung 0009](entscheidungen/0009-nur-session-anmeldung.md)); eine Anfrage nur mit
`Authorization: Bearer …` gilt als nicht angemeldet.

### Ablauf für die SPA

- **CSRF:** Das Backend setzt das Cookie `XSRF-TOKEN` (bei jeder Antwort, auch bei 401). Jede
  schreibende Anfrage, auch Anmeldung und Registrierung, schickt den Wert im Header
  `X-XSRF-TOKEN` zurück. Nach der Anmeldung wird das Token erneuert; Angular liest es dafür
  ohnehin vor jeder Anfrage neu aus dem Cookie.
- **Beim Start:** `GET /api/nutzer/ich/komponenten`. **401** → Login-Seite zeigen, **200** →
  angemeldet, Bereiche nach der Liste einblenden.
- **Anmeldung:** `POST /api/anmeldung` mit `{"email": "...", "passwort": "..."}`.
  - **204**: angemeldet, Session-Cookie gesetzt
  - **401**, `code` `ANMELDUNG_UNGUELTIG`: E-Mail-Adresse oder Passwort falsch
  - **403**, `code` `ANMELDUNG_GESPERRT`: Konto deaktiviert oder nach Fehlversuchen vorübergehend gesperrt
  - **403**, `code` `ANMELDUNG_NICHT_EINGERICHTET`: in Keycloak stehen Pflichtschritte aus
  - **400**, `code` `VALIDIERUNG`: Felder fehlen
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

Antworten unter `/api/**`: **401** ohne Anmeldung (`NICHT_ANGEMELDET`), **403** bei fehlender
Berechtigung (`KEIN_ZUGRIFF`) oder fehlendem CSRF-Token (`CSRF_TOKEN_UNGUELTIG`); zum Format siehe
[Fehlerantworten](#fehlerantworten).

Öffentlich ohne Anmeldung sind nur `/actuator/health/**`, `/actuator/info`,
`POST /api/registrierung`, `POST /api/anmeldung` und im dev-Profil die OpenAPI-Dokumentation.

## Komponenten

Bei der Registrierung wählt der Nutzer Komponenten; sie entscheiden, welche Fachbereiche er
nutzen darf. Die Komponenten gehören seinem Haushalt und liegen in unserer Datenbank
(`haushalt_komponente`), nicht als Rollen in Keycloak. Änderungen greifen so sofort und nicht erst nach einem neuen Login.

| Komponente             | Modul        | API-Pfad              |
|------------------------|--------------|-----------------------|
| `FINANZUEBERSICHT`     | `buchungen`  | `/api/buchungen/**`   |
| `VERTRAGSUEBERSICHT`   | `vertraege`  | `/api/vertraege/**`   |
| `VERSORGUNGSVERTRAEGE` | `versorgung` | `/api/versorgung/**`  |
| `PORTFOLIO`            | `portfolio`  | `/api/portfolio/**`   |

Das Backend prüft bei jeder Anfrage auf einen dieser Pfade, ob der Haushalt des Nutzers die
Komponente gebucht hat, sonst **403** `KEIN_ZUGRIFF`.

Die SPA fragt die gebuchten Komponenten über `GET /api/nutzer/ich/komponenten` ab und blendet
danach Bereiche ein oder aus. Das ist reine Anzeige, der Schutz liegt im Backend.

## Fehlerantworten

Alle Fehler kommen als `ProblemDetail` (RFC 9457, `Content-Type: application/problem+json`). Die
SPA erkennt den Fehler an `code` und zeigt den Text selbst in der Sprache des Nutzers an; `detail`
ist englisch und nur für Entwickler und Logs gedacht.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request contains invalid fields.",
  "instance": "/api/registrierung",
  "code": "VALIDIERUNG",
  "felder": [
    { "feld": "passwort", "code": "Size", "parameter": { "min": 8, "max": 255 } },
    { "feld": "email", "code": "Email", "parameter": {} }
  ]
}
```

Bei `VALIDIERUNG` listet `felder` jeden Verstoß einzeln (ein Feld kann mehrere haben): `code` ist
der Name der Bean-Validation-Annotation (`NotBlank`, `NotEmpty`, `Size`, `Email` …), `parameter`
enthält deren gesetzte Attribute (z.&nbsp;B. `min`, `max`).

- **Fachliche Prüfungen** (z.&nbsp;B. unbekannte oder fremde Kategorie) kommen im selben Format,
  `code` ist ebenfalls ein Name in PascalCase, z.&nbsp;B.
  `{ "feld": "kategorieId", "code": "Unbekannt", "parameter": {} }`. Diese Codes stehen beim Endpunkt.
- **Regeln über mehrere Felder** (z.&nbsp;B. Laufzeitende nach Beginn) hängen am Feld, das die SPA
  markieren soll; `code` ist der Name der Regel, z.&nbsp;B. `LaufzeitendeNachBeginn`.

| `code`                 | Status | Bedeutung                                                     |
|------------------------|--------|---------------------------------------------------------------|
| `VALIDIERUNG`          | 400    | Ungültige Felder im Request-Body, siehe `felder`              |
| `ANFRAGE_UNGUELTIG`    | 400    | Request nicht lesbar (kaputtes JSON, unbekannter Enum-Wert …) |
| `NICHT_ANGEMELDET`     | 401    | Keine oder abgelaufene Anmeldung                              |
| `KEIN_ZUGRIFF`         | 403    | Keine Berechtigung, z.&nbsp;B. Komponente nicht gebucht       |
| `CSRF_TOKEN_UNGUELTIG` | 403    | CSRF-Token fehlt oder ist ungültig                            |
| `NICHT_GEFUNDEN`       | 404    | Unbekannter Pfad oder Datensatz, auch eines fremden Haushalts |
| `METHODE_NICHT_ERLAUBT`| 405    | HTTP-Methode nicht unterstützt                                |
| `KONFLIKT`             | 409    | Daten wurden inzwischen geändert (veraltete `version`)        |
| `INTERNER_FEHLER`      | 500    | Unerwarteter Fehler; Details nur im Log                       |

Dazu kommen die Codes der einzelnen Endpunkte (siehe [Anmeldung](#ablauf-für-die-spa) und
[Registrierung](#registrierung)).

## Geldbeträge und Datum

Geldbeträge haben überall dasselbe Format mit Wert und Währung:

```json
{ "wert": 12.34, "waehrung": "EUR" }
```

- Der Wert ist eine JSON-Zahl mit höchstens 2 Nachkommastellen und darf negativ sein. Summen und
  andere Berechnungen macht das Backend, die SPA formatiert nur.
- Vorerst ist nur `EUR` erlaubt; der Typ ist für weitere Währungen ausgelegt (Fremdwährungen:
  [#30](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/30)). Rechnen geht nur mit
  Beträgen derselben Währung, Summen gruppieren nach Währung.
- Rundung kaufmännisch (`HALF_UP`, 2,345 → 2,35), nur bei Berechnungen; Eingaben mit mehr als 2
  Nachkommastellen werden abgelehnt.
- Ungültige Beträge erscheinen in `felder`, z.&nbsp;B.
  `{ "feld": "preis.wert", "code": "Digits", "parameter": { "integer": 17, "fraction": 2 } }` oder `{ "feld": "preis.waehrung", "code": "ErlaubteWaehrung" }`.

Fachliche Daten (Buchungsdatum, Vertragsbeginn) sind Datumswerte ohne Uhrzeit, im JSON ISO-8601
(`2026-10-31`). „Heute“ bestimmt das Backend in der Zeitzone Europe/Berlin. Zeitstempel sind
ISO-8601 in UTC (`2026-10-31T19:21:54Z`).

## Fachendpunkte

Regeln für alle Endpunkte der Fachmodule (Verträge, Buchungen …):

- **Methoden:** `GET` liest, `POST` legt an (**201** mit `Location`-Header und dem angelegten
  Objekt), `PUT` ändert mit dem ganzen Objekt (**200** mit dem geänderten Objekt), `DELETE` löscht
  (**204**). Kein `PATCH`.
- **IDs:** UUIDs; Pfade unter dem Pfad des Moduls (siehe [Komponenten](#komponenten)).
- **Gleichzeitige Änderungen:** Jedes Objekt hat eine `version`. Die SPA schickt sie beim `PUT`
  zurück; ist sie veraltet, weil ein anderes Mitglied inzwischen geändert hat, antwortet das Backend
  mit **409** `KONFLIKT`, und die SPA lädt neu.
- **Paging** nur, wo Listen groß werden können. Dann `Pageable` als Parameter (`?page=0&size=20&sort=…`)
  und `Page` als Antwort; ausgeliefert als `{"content": [...], "page": {"size", "number",
  "totalElements", "totalPages"}}`.

## Registrierung

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

Antworten: **201** angelegt, **400** ungültige Daten (`VALIDIERUNG`) oder von Keycloak abgelehnt,
z.&nbsp;B. wegen der Passwort-Richtlinie (`REGISTRIERUNG_ABGELEHNT`), **409** E-Mail bereits vergeben
(`EMAIL_BEREITS_VERGEBEN`). Nach 201 ist der Nutzer direkt angemeldet.

Alle Textfelder sind Pflicht und höchstens 255 Zeichen lang, das Passwort mindestens 8 Zeichen;
die übrigen Regeln der Passwort-Richtlinie prüft Keycloak.

Was bei der Registrierung intern passiert und was bei Fehlern zu tun ist, steht unter
[Betrieb](betrieb.md#konten-und-registrierung).
