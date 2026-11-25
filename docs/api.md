# API für die SPA

Vertrag zwischen Backend und SPA: Anmeldung, Endpunkte, Fehlerantworten und Datenformate. Wie
man Endpunkte im Backend baut, steht in [Entwicklung](entwicklung.md).

## Endpunkte

| Endpunkt                          | Zugriff         | Beschreibung                                    |
|-----------------------------------|-----------------|-------------------------------------------------|
| `POST /api/registrierung`         | öffentlich      | Konto in Keycloak und Nutzer anlegen            |
| `GET /api/nutzer/ich`             | angemeldet      | Profil: `vorname`, `name`, `email`              |
| `GET /api/nutzer/ich/komponenten` | angemeldet      | Gebuchte Komponenten, siehe [Komponenten](#komponenten) |
| `PUT /api/nutzer/ich/komponenten` | angemeldet      | Komponenten ändern, siehe [Komponenten](#komponenten) |
| `POST /api/anmeldung`             | öffentlich      | Anmeldung mit E-Mail und Passwort (Session)     |
| `POST /api/abmeldung`             | angemeldet      | Abmeldung, auch bei Keycloak                    |
| `GET /api/vertraege/kategorien`   | `VERTRAGSUEBERSICHT` | Vertragskategorien, siehe [Verträge](#verträge) |
| `POST /api/vertraege`             | `VERTRAGSUEBERSICHT` | Vertrag anlegen, siehe [Verträge](#verträge)    |
| `GET /api/vertraege`              | `VERTRAGSUEBERSICHT` | Verträge auflisten, siehe [Verträge](#verträge) |
| `GET /api/vertraege/{id}`         | `VERTRAGSUEBERSICHT` | Vertrag abrufen, siehe [Verträge](#verträge)    |
| `PUT /api/vertraege/{id}`         | `VERTRAGSUEBERSICHT` | Vertrag ändern, siehe [Verträge](#verträge)     |
| `DELETE /api/vertraege/{id}`      | `VERTRAGSUEBERSICHT` | Vertrag löschen, siehe [Verträge](#verträge)    |
| `POST /api/vertraege/{id}/wiederaufnahme` | `VERTRAGSUEBERSICHT` | Gekündigten Vertrag wieder aufnehmen, siehe [Wieder aufnehmen](#wieder-aufnehmen) |
| `GET /api/vertraege/uebersicht`   | `VERTRAGSUEBERSICHT` | Kennzahlen, siehe [Verträge](#verträge)         |
| `POST /api/vertraege/vorschau`    | `VERTRAGSUEBERSICHT` | Werte fürs Formular berechnen, siehe [Vorschau](#vorschau) |
| `/actuator/health`                | öffentlich      | Health Check, inkl. Probes `liveness`/`readiness`|
| `/actuator/health/systemstatus`   | öffentlich      | Status für die Login-Seite: `db`, `keycloak`     |
| `GET /actuator/info`              | öffentlich      | Version und Umgebung, siehe [Version und Umgebung](#version-und-umgebung) |

Die vollständige API-Beschreibung (OpenAPI 3.1) liegt als [`openapi.json`](openapi.json) im Repo,
z.&nbsp;B. um daraus Typen für die SPA zu erzeugen; sie wird bei jedem Build neu erzeugt. Antworten
enthalten immer alle Felder, was fehlt, ist `null`. Zur Laufzeit
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

### Version und Umgebung

`GET /actuator/info` nennt Version und Build-Zeit des Backends, den Commit und die Umgebung
(`stage`: `DEV` oder `PROD`). Der Endpunkt steht nicht in `openapi.json`, der Typ in der SPA ist
von Hand geschrieben. `git` ist leer (`{}`), wenn das Backend ohne `.git` gebaut wurde.

```json
{
  "build": { "version": "0.1.0", "time": "2026-11-05T17:00:00Z" },
  "git": { "commit": { "id": "48eaea5" } },
  "app": { "stage": "DEV" }
}
```

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
  angemeldet, Bereiche nach `komponenten` einblenden.
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
danach Bereiche ein oder aus. Das ist reine Anzeige, der Schutz liegt im Backend. Die Antwort
enthält die Komponenten sortiert und die `version` des Haushalts:

```json
{ "komponenten": ["FINANZUEBERSICHT", "PORTFOLIO"], "version": 3 }
```

`PUT /api/nutzer/ich/komponenten` (CSRF-Token nötig) ersetzt die Auswahl, z.&nbsp;B. in den
Einstellungen: Body wie die Antwort, mit der zuletzt gelesenen `version` (Schema
`KomponentenAenderung`) → **200** mit der neuen Auswahl und der neuen `version`. Die Änderung gilt für
den ganzen Haushalt und sofort, auch für die Zugriffsprüfung. Wird eine Komponente ausgeschaltet,
**bleiben ihre Daten erhalten**, nur der Zugriff ist gesperrt; nach dem Einschalten sind sie wieder da.
Dieselbe Auswahl noch einmal ändert nichts, auch nicht die `version`.

- **400** `VALIDIERUNG`: keine Komponente (`komponenten`, `NotEmpty`) oder `version` fehlt
- **400** `ANFRAGE_UNGUELTIG`: unbekannte Komponente oder `null` in der Liste
- **409** `KONFLIKT`: `version` veraltet. Sie gilt für den ganzen Haushalt, ändert sich also auch,
  wenn sich später etwas anderes daran ändert (z.&nbsp;B. Mitglieder); die SPA lädt dann neu.
- **404** `NICHT_GEFUNDEN` (GET und PUT): Das Konto gehört zu keinem Haushalt. Das kommt nur nach
  Fehlern oder Eingriffen von Hand vor (z.&nbsp;B. Konto direkt in Keycloak angelegt).

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
| `WIEDERAUFNAHME_NICHT_MOEGLICH` | 409 | Vertrag ist weder gekündigt noch beendet, siehe [Wieder aufnehmen](#wieder-aufnehmen) |
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
- Eingaben mit mehr als 2 Nachkommastellen werden abgelehnt. Berechnete Beträge rechnet das Backend
  ungerundet und rundet erst bei der Ausgabe kaufmännisch (2,345 → 2,35); verbindlich ist nur der
  Betrag je Abbuchung, umgerechnete Werte und Summen können um wenige Cent abweichen
  ([ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)).
- Ungültige Beträge erscheinen in `felder`, z.&nbsp;B.
  `{ "feld": "preis.wert", "code": "Digits", "parameter": { "integer": 17, "fraction": 2 } }` oder `{ "feld": "preis.waehrung", "code": "ErlaubteWaehrung" }`.

Fachliche Daten (Buchungsdatum, Vertragsbeginn) sind Datumswerte ohne Uhrzeit, im JSON ISO-8601
(`2026-10-31`). „Heute“ bestimmt das Backend in der Zeitzone Europe/Berlin. Fachliche Datumswerte in
Anfragen liegen zwischen `1900-01-01` und `2199-12-31`, sonst Feldfehler `PlausiblesDatum`. Zeitstempel sind
ISO-8601 in UTC (`2026-10-31T19:21:54Z`).

## Fachendpunkte

Regeln für alle Endpunkte der Fachmodule (Verträge, Buchungen …):

- **Methoden:** `GET` liest, `POST` legt an (**201** mit `Location`-Header und dem angelegten
  Objekt), `PUT` ändert mit dem ganzen Objekt (**200** mit dem geänderten Objekt), `DELETE` löscht
  (**204**). Kein `PATCH`. Ausnahme: Berechnungen ohne Speichern mit Formularwerten im Body (z.&nbsp;B.
  `POST /api/vertraege/vorschau`) antworten mit **200** ohne `Location`.
- **IDs:** UUIDs im Standardformat als String, z.&nbsp;B. `7cb7a2b8-38a6-432d-8546-03b3d74f76ec`, im Pfad
  wie im JSON. Eine ID in anderem Format ergibt **400** `ANFRAGE_UNGUELTIG`, eine unbekannte oder fremde
  ID **404** `NICHT_GEFUNDEN`. Pfade liegen unter dem Pfad des Moduls (siehe [Komponenten](#komponenten)).
- **Gleichzeitige Änderungen:** Jedes Objekt hat eine `version`. Die SPA schickt sie beim `PUT` im
  Body zurück (Pflichtfeld, fehlt sie: **400** `VALIDIERUNG`) und beim `DELETE` als Parameter
  (`?version=3`, Pflicht, fehlt sie: **400** `ANFRAGE_UNGUELTIG`); ist sie veraltet, weil ein anderes
  Mitglied inzwischen geändert hat, antwortet das Backend mit **409** `KONFLIKT`, ändert bzw. löscht
  nichts, und die SPA lädt neu. So geht keine Änderung unbemerkt verloren. Die
  Antwort auf das `PUT` enthält die neue `version` für die nächste Änderung; ein `PUT` ohne
  tatsächliche Änderung lässt sie gleich. Mitgeschickte Felder, die nur in der Antwort stehen (z.&nbsp;B.
  `id`, berechnete Werte), ignoriert das Backend.
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

## Verträge

Komponente `VERTRAGSUEBERSICHT`. Datumsangaben und Beträge im Format aus
[Geldbeträge und Datum](#geldbeträge-und-datum).

### Kategorien

`GET /api/vertraege/kategorien` liefert die Auswahlliste in fester Reihenfolge:

```json
[{ "id": "7cb7a2b8-38a6-432d-8546-03b3d74f76ec", "schluessel": "VERSICHERUNG", "name": null }]
```

Standardkategorien haben einen `schluessel`, den die SPA übersetzt: `STREAMING_ABOS`,
`SOFTWARE_APPS`, `VERSICHERUNG`, `MOBILFUNK_INTERNET`, `MITGLIEDSCHAFTEN`, `GEBUEHREN_BEITRAEGE`,
`SONSTIGES`; ihre IDs sind in allen Umgebungen gleich. Eigene Kategorien eines Haushalts (später,
[#34](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/34)) haben stattdessen einen `name`.

### Vertrag anlegen

`POST /api/vertraege` (CSRF-Token nötig) → **201** mit `Location` und dem Vertrag:

```json
{
  "anbieter": "Musterversicherung AG",
  "bezeichnung": "Kfz-Versicherung",
  "kategorieId": "7cb7a2b8-38a6-432d-8546-03b3d74f76ec",
  "vertragsnummer": "KFZ-4711-23",
  "preise": [
    { "gueltigAb": "2023-01-01", "betrag": { "wert": 549.00, "waehrung": "EUR" } },
    { "gueltigAb": "2026-01-01", "betrag": { "wert": 586.80, "waehrung": "EUR" } }
  ],
  "zahlweise": "JAEHRLICH",
  "zahlungsweg": "LASTSCHRIFT",
  "anteilProzent": null,
  "einmalkosten": [
    { "bezeichnung": "Anschlussgebühr", "betrag": { "wert": 49.00, "waehrung": "EUR" }, "faelligAm": "2023-01-15" }
  ],
  "preissteigerung": { "art": "PROZENT", "prozent": 2.5, "betrag": null, "rhythmus": "JAEHRLICH", "naechsteAm": "2027-01-01" },
  "preisgarantieBis": null,
  "erinnerungSonderkuendigung": true,
  "kuendbar": true,
  "beginn": "2023-01-01",
  "laufzeitende": "2023-12-31",
  "verlaengerung": "ZWOELF_MONATE",
  "kuendigungsfristMonate": 1,
  "gekuendigtZum": null,
  "notiz": null
}
```

| Feld | Pflicht | Werte und Regeln |
|---|---|---|
| `anbieter` | ja | höchstens 200 Zeichen |
| `bezeichnung` | nein | höchstens 200 Zeichen; leer = die SPA zeigt den Anbieter |
| `kategorieId` | ja | Standardkategorie oder eigene des Haushalts, sonst Feldfehler `Unbekannt` |
| `vertragsnummer` | nein | höchstens 100 Zeichen |
| `preise` | ja | 1–1200 Einträge `{ gueltigAb, betrag, automatisch }` in beliebiger Reihenfolge, siehe [Preise](#preise) |
| `zahlweise` | ja | `MONATLICH`, `VIERTELJAEHRLICH`, `HALBJAEHRLICH`, `JAEHRLICH` |
| `zahlungsweg` | nein | `LASTSCHRIFT`, `KREDITKARTE`, `PAYPAL`, `WERO`, `UEBERWEISUNG`, `DAUERAUFTRAG` |
| `anteilProzent` | nein | eigener Anteil an einem geteilten Vertrag, z.&nbsp;B. `50` oder `33.3`; leer = 100&nbsp;%. Zahl von 1 bis 100 mit höchstens einer Nachkommastelle (Feldfehler `DecimalMin`, `DecimalMax`, `Digits`). Gespeichert wie eingegeben, auch `100`; die Antwort liefert ihn mit einer Nachkommastelle (`50.0`). Übersichten zählen nur diesen Anteil der Kosten, siehe [Antwort](#antwort) |
| `einmalkosten` | ja | 0–100 Einträge `{ bezeichnung, betrag, faelligAm }` in beliebiger Reihenfolge, leer `[]`; siehe [Einmalkosten](#einmalkosten) |
| `preissteigerung` | nein | „Preis steigt regelmäßig“, `null` wenn nicht; siehe [Preissteigerung](#preissteigerung) |
| `preisgarantieBis` | nein | bis einschließlich dieses Tages erhöht die [Preissteigerung](#preissteigerung) nicht, auch vergangene Termine nicht; auch ohne `preissteigerung` erlaubt (dann nur gespeichert) |
| `erinnerungSonderkuendigung` | ja | „Vor jeder Preiserhöhung daran erinnern“, im Formular standardmäßig `true`. Wird vorerst nur gespeichert, die Erinnerung selbst kommt mit den zentralen Meldungen ([Frontend #49](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/49)) |
| `kuendbar` | ja | `false` z.&nbsp;B. für den Rundfunkbeitrag: `verlaengerung` und `kuendigungsfristMonate` werden nicht gespeichert, `beginn` und `laufzeitende` bleiben (z.&nbsp;B. „seit 2013“) |
| `beginn` | ja | Vertragsbeginn und **Anker des Zahlungsrhythmus**: ab diesem Tag wird im Takt der Zahlweise abgebucht (wann der Anbieter genau abbucht, erfasst der Nutzer nicht); liegt er nach heute, ist der Vertrag `NICHT_BEGONNEN` |
| `laufzeitende` | nein | nicht vor `beginn` (Feldfehler `LaufzeitendeNachBeginn` an `laufzeitende`) |
| `verlaengerung` | nein | nach dem Laufzeitende: `ZAHLUNGSINTERVALL`, `MONATLICH`, `ZWOELF_MONATE`, `KEINE` |
| `kuendigungsfristMonate` | nein | 0–24 |
| `gekuendigtZum` | nein | gesetzt = gekündigt; ein vergangenes Datum erfasst einen beendeten Vertrag, eines vor `beginn` einen stornierten (wird nicht abgelehnt, weil trotzdem Einmalkosten anfallen können) |
| `notiz` | nein | höchstens 1000 Zeichen |

Alle Datumsfelder zwischen `1900-01-01` und `2199-12-31` (Feldfehler `PlausiblesDatum`, siehe
[Geldbeträge und Datum](#geldbeträge-und-datum)). Leere Texte speichert das Backend als `null`. Einen **Status** schickt die SPA nicht mit; er wird
berechnet (siehe unten).

#### Preise

Ein Vertrag hat eine Liste von Preisen mit Gültigkeitsdatum, z.&nbsp;B. frühere Preise, angekündigte
Erhöhungen oder Aktionspreise. Jeder Eintrag hat ein `gueltigAb`, der früheste genau den `beginn`. Ein Preis gilt
von seinem `gueltigAb` bis zum nächsten Eintrag.

`betrag` in der [Antwort](#antwort) ist der **aktuelle Preis**: der heute gültige, bei einem beendeten Vertrag der
an seinem letzten Tag gültige (ein Eintrag nach dem Vertragsende wurde nie gezahlt); beginnt der Vertrag erst in
der Zukunft, der erste Eintrag. Ist seit dem letzten Speichern eine Stufe der Preissteigerung fällig geworden, ist
es diese (`betragErrechnet`, siehe [Fällige Stufen](#faellige-stufen)).

`automatisch` (`true`/`false`, fehlt = `false`) kennzeichnet einen Eintrag, den das Backend aus der
[Preissteigerung](#preissteigerung) angelegt hat. Die SPA schickt ihn beim Ändern unverändert zurück; ob er bleibt,
entscheidet das Backend: nur wenn es einen gespeicherten automatischen Eintrag mit gleichem `gueltigAb` und gleichem
`betrag` gibt. Ein geänderter Eintrag (oder einer, den das Backend nicht kennt) wird als eingetragener Preis
gespeichert, ohne Fehler. Sonst sind automatische Einträge normale Preise: Der Nutzer kann sie ändern und löschen,
und es gelten dieselben Regeln (z.&nbsp;B. `PreisNichtVorBeginn`, wenn der Beginn hinter sie verschoben wird).

| Regel | Feldfehler (an `preise[i]…`, `i` = Position in der Anfrage) |
|---|---|
| mindestens ein, höchstens 1200 Einträge (genug für automatische Stufen, vierteljährlich von 1900 bis 2199) | `NotEmpty` bzw. `Size` an `preise` |
| `gueltigAb` bei jedem Eintrag | `NotNull` an `preise[i].gueltigAb` |
| kein `gueltigAb` doppelt (die spätere Wiederholung ist der Fehler) | `GueltigAbEindeutig` an `preise[i].gueltigAb` |
| kein `gueltigAb` vor `beginn`, auch wenn der Beginn später nach hinten verschoben wird | `PreisNichtVorBeginn` an `preise[i].gueltigAb` |
| der früheste Eintrag gilt genau ab `beginn`, nicht später (davor meldet `PreisNichtVorBeginn`) | `ErsterPreisAbBeginn` an `preise[i].gueltigAb` dieses Eintrags |
| `betrag` größer 0 | `PositiverBetrag` an `preise[i].betrag` |
| alle Beträge in derselben Währung wie der erste (eine Währung am Vertrag kommt mit [#30](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/30)) | `GleicheWaehrung` an `preise[i].betrag.waehrung` |

Dazu wie bei jedem Datum `PlausiblesDatum` an `preise[i].gueltigAb`. Die Antwort liefert `preise` sortiert nach
`gueltigAb`, jeden Eintrag mit `art`:

| `art` | Eintrag |
|---|---|
| `FRUEHER` | vor dem aktuellen |
| `AKTUELL` | genau einer: der, aus dem `betrag` stammt (ist `betrag` errechnet, der, auf dem er aufbaut) |
| `ANGEKUENDIGT` | nach dem aktuellen, beginnt spätestens am letzten Vertragstag |
| `NACH_ENDE` | beginnt nach dem letzten Vertragstag (`gekuendigtZum` bzw. Laufzeitende ohne Verlängerung, das frühere), gilt also nie |

Damit trennt die SPA im Formular „Betrag“ von den übrigen Preisen und beschriftet die Preisliste („aktuell“,
„angekündigt“, „nach Vertragsende“), ohne mit dem Datum des Browsers zu vergleichen. Bei einem beendeten Vertrag
ist der zuletzt gezahlte `AKTUELL`. `art` wird beim Ändern ignoriert.

#### Einmalkosten

Einmalige Kosten wie Anschlussgebühr, Aufnahmegebühr oder Hardware. Sie zählen **nicht** zu den laufenden Kosten
(`kostenProMonat`, `kostenImJahr`), sondern stehen getrennt in `berechnet.einmalkosten` und in der
[Übersicht](#vertragsübersicht). `einmalkosten` ist Pflicht, damit ein `PUT` ohne das Feld sie nicht still löscht;
ohne Einmalkosten schickt die SPA `[]`. Fällig vor `beginn` oder nach dem Vertragsende ist erlaubt (z.&nbsp;B.
vorab bezahlte Hardware); gleiche Einträge auch (zwei Geräte am selben Tag).

| Regel | Feldfehler (an `einmalkosten[i]…`, `i` = Position in der Anfrage) |
|---|---|
| Liste angegeben, höchstens 100 Einträge | `NotNull` bzw. `Size` an `einmalkosten` |
| `bezeichnung` („Wofür“) angegeben, höchstens 200 Zeichen | `NotBlank` bzw. `Size` an `einmalkosten[i].bezeichnung` |
| `betrag` größer 0 | `PositiverBetrag` an `einmalkosten[i].betrag` |
| `betrag` in derselben Währung wie der erste Preis | `GleicheWaehrung` an `einmalkosten[i].betrag.waehrung` |
| `faelligAm` angegeben | `NotNull` an `einmalkosten[i].faelligAm` |

Dazu `PlausiblesDatum` an `einmalkosten[i].faelligAm`. Die Antwort liefert `einmalkosten` sortiert nach
`faelligAm`, dann `bezeichnung`.

#### Preissteigerung

Die Regel, nach der ein Vertrag regelmäßig teurer wird: `{ art, prozent, betrag, rhythmus, naechsteAm }`. Daraus
legt das Backend beim Speichern die **fälligen Stufen** als Preise an und rechnet eine **Prognose** (siehe
[Antwort](#antwort)).

- **Termine:** von `naechsteAm` aus im Rhythmus, immer von dort gezählt; liegt `naechsteAm` auf einem Monatsletzten,
  bleiben auch die weiteren am Monatsletzten (30.04. vierteljährlich → 31.07., 31.10., 31.01.). Termine bis
  einschließlich `preisgarantieBis` erhöhen nicht, übersprungene Termine erhöhen nichts.
- **Schritte:** Jeder Schritt wird kaufmännisch auf Cent gerundet, der nächste rechnet mit dem gerundeten Preis
  weiter ([ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)): 100&nbsp;€ + 2,5&nbsp;% =
  102,50&nbsp;€, + 2,5&nbsp;% = 105,06&nbsp;€.
- <a id="faellige-stufen"></a>**Fällige Stufen:** Beim Anlegen und Ändern legt das Backend jeden Termin bis
  einschließlich heute (bei einem beendeten Vertrag bis zu seinem letzten Tag) als Preis mit `automatisch: true` an
  ([ADR 0022](entscheidungen/0022-automatische-preiserhoehungen-speichern.md)). Die Stufen setzen beim letzten
  Eintrag bis heute an (dem Ausgangseintrag, eingetragen oder automatisch) und beginnen am ersten Termin danach.
  Angekündigte Einträge zählen dafür nicht: Bei einem Vertrag ab 2021 mit einem angekündigten Preis 2027 entstehen
  die Stufen bis heute trotzdem. Zwischen zwei Einträgen vor heute rechnet die Regel nicht; eine Lücke dort ist
  gewollt.
- <a id="betrachtet-bis"></a>**Gelöschte Stufen bleiben gelöscht:** Jedes Speichern mit Regel merkt sich in
  `preissteigerungBetrachtetBis` den Tag, bis zu dem es die Termine betrachtet hat (heute, bei einem beendeten Vertrag
  sein letzter Tag; der Wert wird nur später). Ein Termin bis dahin entsteht nie wieder, auch nicht die neueste Stufe
  oder alle, wenn der Nutzer sie gelöscht hat: Löschen heißt „an diesem Termin keine Erhöhung“, die Prognose rechnet
  vom letzten verbliebenen Eintrag weiter. Ohne Regel bleibt der Wert, wie er ist; ein Vertrag, der erst später eine
  Regel bekommt, erhält also alle Stufen seit Beginn, aus- und wieder eingeschaltet dagegen keine gelöschten.
- **Regel geändert oder entfernt:** wirkt nur nach vorn. Gespeicherte Einträge, auch automatische, bleiben; neue
  Stufen rechnen mit der neuen Regel ab dem Ausgangseintrag, nur an Terminen nach `preissteigerungBetrachtetBis`.
  Dasselbe gilt, wenn `naechsteAm`, der Beginn oder `preisgarantieBis` nachträglich geändert werden: Termine, die schon
  betrachtet waren (auch solche, die damals in der Garantie lagen), entstehen nicht nachträglich.
- **Seit dem letzten Speichern fällig:** Eine Stufe, die erst nach dem Speichern fällig wird (oder bei Verträgen,
  die vor 0.2.2 gespeichert wurden), legt erst das nächste Speichern an. Bis dahin rechnet das Backend sie beim
  Lesen ein: `betrag`, `kostenProMonat`, `kostenImJahr` und `eigenerAnteil` rechnen mit ihr, `betragErrechnet` ist
  `true`, im `preisverlauf` steht sie als `ERRECHNET`. In `preise` steht sie nicht (dort nur Gespeichertes, die SPA
  schickt `preise` beim Ändern zurück); `AKTUELL` ist dann der gespeicherte Eintrag, auf dem sie aufbaut.

| Feld | Pflicht | Werte und Regeln |
|---|---|---|
| `art` | ja | `PROZENT` oder `BETRAG` |
| `prozent` | bei `PROZENT` | größer 0, höchstens 100, höchstens zwei Nachkommastellen (z.&nbsp;B. `2.75`); die Antwort liefert zwei (`2.50`) |
| `betrag` | bei `BETRAG` | größer 0, in derselben Währung wie der erste Preis |
| `rhythmus` | ja | `VIERTELJAEHRLICH`, `HALBJAEHRLICH`, `JAEHRLICH`, `ZWEIJAEHRLICH`, `DREIJAEHRLICH` |
| `naechsteAm` | ja | Datum der nächsten (bzw. ersten) Erhöhung; Anker im Rhythmus wie `beginn` für die Abbuchungen, darf in der Vergangenheit liegen. Die [Vorschau](#vorschau) schlägt `beginn` plus einen Rhythmus vor (`vorschlagNaechsteAm`) |

| Regel | Feldfehler (an `preissteigerung.…`) |
|---|---|
| `art`, `rhythmus`, `naechsteAm` angegeben | `NotNull` |
| zur `art` genau der passende Wert (`prozent` bzw. `betrag`), der andere leer | `PassenderWert` an `prozent` bzw. `betrag` |
| `prozent` größer 0, höchstens 100, zwei Nachkommastellen | `DecimalMin`, `DecimalMax`, `Digits` an `prozent` |
| `betrag` größer 0, Währung wie der erste Preis | `PositiverBetrag` an `betrag`, `GleicheWaehrung` an `betrag.waehrung` |

Dazu `PlausiblesDatum` an `naechsteAm` und an `preisgarantieBis`.

### Antwort

Die Antwort enthält die gespeicherten Felder wie oben, die Kategorie als Objekt (wie bei den
Kategorien) statt `kategorieId`, dazu `id` und `version` sowie `betrag`, den aktuellen Preis (siehe
[Preise](#preise)) mit `betragErrechnet` (`true`, wenn er eine seit dem letzten Speichern fällige Stufe ist, siehe
[Fällige Stufen](#faellige-stufen); die SPA zeigt dann „errechnet“). Beide sind berechnet und werden beim Ändern
ignoriert, ebenso `preissteigerungBetrachtetBis` (Datum oder `null`, siehe
[Gelöschte Stufen bleiben gelöscht](#betrachtet-bis)); den schickt die SPA nur an die [Vorschau](#vorschau).
`unterbrechungen` (`[{ id, von, bis, art }]`, sortiert nach `von`, siehe [Wieder aufnehmen](#wieder-aufnehmen)) ist
gespeichert, wird beim Ändern aber nicht als Liste zurückgeschickt, sondern über `unterbrechungIds`.
Was das Backend sonst berechnet, steht in `berechnet`; das hängt vom heutigen Tag ab und wird beim Ändern
nicht zurückgeschickt:

```json
"betrag": { "wert": 586.80, "waehrung": "EUR" },
"betragErrechnet": false,
"preise": [
  { "gueltigAb": "2023-01-01", "betrag": { "wert": 549.00, "waehrung": "EUR" }, "art": "FRUEHER", "automatisch": false },
  { "gueltigAb": "2026-01-01", "betrag": { "wert": 586.80, "waehrung": "EUR" }, "art": "AKTUELL", "automatisch": false }
],
"berechnet": {
  "status": "AKTIV",
  "kostenProMonat": { "wert": 48.90, "waehrung": "EUR" },
  "kostenImJahr": { "wert": 586.80, "waehrung": "EUR" },
  "kostenImJahrMitPrognose": false,
  "jahr": 2026,
  "eigenerAnteil": null,
  "einmalkosten": { "wert": 49.00, "waehrung": "EUR" },
  "vertragsende": null,
  "aktuellerBeginn": "2023-01-01",
  "wiederaufnahme": null,
  "naechstePreisaenderung": { "ab": "2027-01-01", "betrag": { "wert": 601.47, "waehrung": "EUR" }, "art": "PROGNOSE" },
  "prognose": [
    { "ab": "2027-01-01", "betrag": { "wert": 601.47, "waehrung": "EUR" } },
    { "ab": "2028-01-01", "betrag": { "wert": 616.51, "waehrung": "EUR" } },
    { "ab": "2029-01-01", "betrag": { "wert": 631.92, "waehrung": "EUR" } }
  ],
  "preisverlauf": [
    { "ab": "2023-01-01", "betrag": { "wert": 549.00, "waehrung": "EUR" }, "art": "GEZAHLT" },
    { "ab": "2026-01-01", "betrag": { "wert": 586.80, "waehrung": "EUR" }, "art": "GEZAHLT" },
    { "ab": "2027-01-01", "betrag": { "wert": 601.47, "waehrung": "EUR" }, "art": "PROGNOSE" },
    …
  ],
  "kuendigung": { "art": "KUENDIGEN_BIS", "kuendigenBis": "2026-11-30", "zum": "2026-12-31", "fristMonate": 1, "dringend": true }
}
```

- **Status**, die erste passende Zeile:

  | Bedingung | `status` |
  |---|---|
  | `gekuendigtZum` vor `beginn` (läuft nie; gekündigt zum Beginn läuft er einen Tag) | `STORNIERT` |
  | `beginn` nach heute, auch wenn schon gekündigt | `NICHT_BEGONNEN` |
  | heute liegt in einer [Unterbrechung](#wieder-aufnehmen) (Wiederaufnahme in der Zukunft) | `UNTERBROCHEN` |
  | `gekuendigtZum` vorbei, oder kündbar mit Verlängerung `KEINE` und Laufzeitende vorbei | `BEENDET` |
  | `gekuendigtZum` kommt noch | `GEKUENDIGT` |
  | sonst | `AKTIV` |
- **`vertragsende`:** der letzte Vertragstag: `gekuendigtZum` oder das Laufzeitende eines kündbaren Vertrags mit
  Verlängerung `KEINE`, bei beiden Daten das frühere; `null` bei offenem Ende (auch bei einem Laufzeitende mit
  Verlängerung). Die SPA zeigt damit „Beginn – Ende“ bei beendeten Verträgen. Abbuchungen, Prognose und Preise nach
  diesem Tag zählen nicht.
- **`aktuellerBeginn`:** der Beginn der letzten Phase: nach einer [Unterbrechung](#wieder-aufnehmen) der Tag der
  Wiederaufnahme (`bis` + 1 der letzten), sonst `beginn`. Ab ihm zählen Laufzeit und Verlängerung; die SPA zeigt
  damit „wieder seit …“.
- **`wiederaufnahme`:** `{ fruehestens, vorschlag }`, wenn sich der Vertrag [wieder aufnehmen](#wieder-aufnehmen)
  lässt (Status `GEKUENDIGT` oder `BEENDET` mit `vertragsende`), sonst `null`. `fruehestens` ist `vertragsende` + 1,
  `vorschlag` `fruehestens` oder heute, was später liegt (Vorbelegung im Dialog).
- **`kostenProMonat`:** `betrag` ÷ Monate der Zahlweise (gerundet nach
  [ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)); umgerechnet, nicht abgebucht.
- **`kostenImJahr`:** Summe aller Abbuchungen im Kalenderjahr `jahr` (dem laufenden Jahr laut Backend; die SPA
  beschriftet damit „Kosten <Jahr>“ statt mit der Uhr des Browsers), jede mit dem Preis, der an ihrem Tag gilt.
  Abgebucht wird ab `beginn` im Takt der Zahlweise (1, 3, 6 oder 12 Monate), immer vom Beginn aus gezählt (Beginn
  31.01. monatlich: 28.02., 31.03.), auch schon erfolgte Abbuchungen und die eines noch nicht begonnenen Vertrags,
  nicht nach `vertragsende` (eine Abbuchung genau an diesem Tag zählt noch; ein stornierter Vertrag hat keine).
  In einer [Unterbrechung](#wieder-aufnehmen) wird nichts abgebucht; danach beginnt der Takt neu am Tag der
  Wiederaufnahme (Beginn 15.01. monatlich, unterbrochen 01.06.–31.08.: bis 15.05., dann 01.09., 01.10. …).
  Ohne Abbuchungen im Jahr 0. Eine Abbuchung nach einer errechneten Stufe rechnet mit ihr; ab einem Termin der
  [Prognose](#prognose) mit dem prognostizierten Preis, dafür zählen alle Termine im Jahr, nicht nur die drei aus
  `prognose`.
- **`kostenImJahrMitPrognose`:** `true`, wenn eine Abbuchung in `kostenImJahr` mit einem nicht gespeicherten Preis
  rechnet, einem prognostizierten oder einem [errechneten](#faellige-stufen); die SPA zeigt dann „inkl. Prognose“.
  Gespeicherte automatische Preise zählen nicht dazu.
- **`eigenerAnteil`:** `{ betrag, kostenProMonat, kostenImJahr }`, der eigene Anteil an `betrag` (je Abbuchung,
  für „Dein Anteil: 50&nbsp;% von 586,80&nbsp;€ = 293,40&nbsp;€ jährlich“), `kostenProMonat` und `kostenImJahr` bei
  einem `anteilProzent` unter 100; sonst `null` (dann sind die vollen Kosten der eigene Anteil). Der Anteil wird
  jeweils auf den ungerundeten Wert angewendet und erst dann gerundet
  ([ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)): 13,99&nbsp;€ monatlich zu 33,3&nbsp;% sind
  4,66&nbsp;€ je Abbuchung und im Monat und 55,90&nbsp;€ im Jahr. Weil jeder Wert für sich gerundet wird, ergibt
  `eigenerAnteil.betrag` umgerechnet nicht zwingend cent-genau `eigenerAnteil.kostenProMonat`. `betrag` und
  `naechstePreisaenderung` bleiben die vollen Beträge, denn so viel wird abgebucht.
- **`einmalkosten`:** Summe aller [Einmalkosten](#einmalkosten) des Vertrags, unabhängig vom Jahr und mit dem vollen
  Betrag (ohne Anteil; die Details zeigen sie als „Gesamt“). Ohne Einträge 0 in der Währung der Preise.
- **`naechstePreisaenderung`:** `{ ab, betrag, art }`, der Eintrag der Preisliste nach dem heute gültigen, `art`
  `ANGEKUENDIGT`. Beginnt der Vertrag erst in der Zukunft, ist der erste Eintrag keine Änderung (er ist schon
  `betrag`). Sonst der erste Preis der [Prognose](#prognose) mit `art` `PROGNOSE`; ein angekündigter Eintrag kommt
  immer zuerst, weil die Prognose erst danach beginnt. Eine errechnete Stufe ist keine Änderung, sie ist schon
  `betrag`. `null`, wenn keine bekannt ist, der Vertrag beendet, storniert oder unterbrochen ist oder der Eintrag
  nach dem Vertragsende liegt.
- <a id="prognose"></a>**`prognose`:** die nächsten drei Preise `{ ab, betrag }` aus der
  [Preissteigerung](#preissteigerung) nach heute; leer ohne Regel oder bei einem beendeten, stornierten oder
  unterbrochenen Vertrag.
  Sie setzt beim letzten Eintrag der Preisliste an, samt den [errechneten Stufen](#faellige-stufen) bis heute
  (ein Termin heute ist schon fällig, nicht Prognose). Übersprungen werden Termine bis einschließlich des
  `gueltigAb` dieses Eintrags, bis einschließlich `preisgarantieBis` und nach dem Vertragsende; Termine und Rundung
  wie unter [Preissteigerung](#preissteigerung). Trägt der Nutzer den echten neuen Preis ein, rechnet die Prognose von
  dort weiter. `betrag` und `kostenProMonat` rechnen nicht mit der Prognose.
- **`preisverlauf`:** die Punkte für das Diagramm `{ ab, betrag, art }`, sortiert nach `ab`: die Einträge der
  [Preisliste](#preise) (`FRUEHER` und `AKTUELL` als `GEZAHLT`, auch automatische; `ANGEKUENDIGT` als
  `ANGEKUENDIGT`; `NACH_ENDE` fallen weg), die seit dem letzten Speichern fälligen Stufen (`ERRECHNET`, siehe
  [Fällige Stufen](#faellige-stufen)) und die drei aus `prognose` (`PROGNOSE`). Die SPA zeichnet nur.
- **Kündigung:** `art` ist die erste passende Zeile:

  | Bedingung | `art` | Daten |
  |---|---|---|
  | Status `BEENDET` | `BEENDET` | keine |
  | Status `STORNIERT` | `STORNIERT` | keine |
  | Status `UNTERBROCHEN` | `WIEDER_AB` | `zum` = Tag der Wiederaufnahme |
  | Status `GEKUENDIGT`, oder `NICHT_BEGONNEN` und schon gekündigt | `GEKUENDIGT` | `zum` = `gekuendigtZum` |
  | nicht kündbar | `NICHT_KUENDBAR` | keine |
  | Verlängerung `KEINE` mit Laufzeitende | `ENDET` | `zum` = Laufzeitende |
  | kein Vertragsende bestimmbar | `KEINE_LAUFZEIT` | keine |
  | Laufzeitende vorbei oder leer, Verlängerung um einen Monat (`MONATLICH` oder `ZAHLUNGSINTERVALL` bei monatlicher Zahlweise) | `MONATLICH_KUENDBAR` | `kuendigenBis`, `zum`; nie `dringend` |
  | sonst | `KUENDIGEN_BIS` | `kuendigenBis`, `zum`; `dringend` bei höchstens 45 Tagen bis `kuendigenBis` |

  Ein noch nicht begonnener Vertrag wird wie ein laufender berechnet (er lässt sich schon kündigen).
  - **Vertragsende** (für `zum`): mit Laufzeitende dieses, so oft um die Verlängerung (Zahlungsintervall, 1 oder
    12 Monate) verlängert, bis `kuendigenBis` nicht in der Vergangenheit liegt; gezählt vom Laufzeitende aus, ein
    Monatsende bleibt eins. Ohne Laufzeitende endet die erste Laufzeit eine Verlängerung nach `aktuellerBeginn`
    (ohne Unterbrechung `beginn`)
    (`ZAHLUNGSINTERVALL` ein Zahlungsintervall, `MONATLICH` ein Monat, `ZWOELF_MONATE` zwölf Monate), jede weitere
    entsprechend später, immer **vom Beginn aus** gezählt: Ende ist der Tag vor dem Beginn-Tag im jeweiligen Monat;
    gibt es den Tag dort nicht, der Monatsletzte (§ 188 Abs. 3 BGB). Beginn 15.03. monatlich → 14.04., 14.05.;
    Beginn 31.01. → 28.02., 30.03., 30.04. Ohne `verlaengerung` gilt nur das Laufzeitende selbst.
  - **`kuendigenBis`** = Vertragsende minus Frist. Endet der Vertrag am Monatsende, ist auch
    `kuendigenBis` ein Monatsende (30.11. mit einem Monat Frist → 31.10.).
  - **`KEINE_LAUFZEIT`** gibt es auch bei einem Laufzeitende ohne Angabe zur Verlängerung, sobald
    `kuendigenBis` vorbei ist; vorher ist es `KUENDIGEN_BIS`. Die SPA zeigt dafür „Kein Kündigungstermin
    bestimmbar“.
  - `fristMonate` ist die hinterlegte Frist (`null`, wenn keine angegeben ist; gerechnet wird dann mit 0).

Fehler: **400** `VALIDIERUNG` mit den Feldfehlern aus der Tabelle (und den üblichen wie `NotBlank`,
`Size`, `Max`, `DecimalMin`, `DecimalMax`, `Digits`).

### Verträge auflisten

`GET /api/vertraege` → **200** mit allen Verträgen des Haushalts, je Vertrag wie in der
[Antwort](#antwort) oben (die Details brauchen also keinen eigenen Aufruf). Ohne Verträge `[]`, kein
Paging. Sortiert nach Dringlichkeit der Kündigung (`berechnet.kuendigung.art`):

| Rang | `art` | innerhalb nach |
|---|---|---|
| 1 | `KUENDIGEN_BIS` und `ENDET` gemeinsam | `kuendigenBis` bzw. `zum` |
| 2 | `MONATLICH_KUENDBAR` | `kuendigenBis` |
| 3 | `KEINE_LAUFZEIT` | – |
| 4 | `NICHT_KUENDBAR` | – |
| 5 | `GEKUENDIGT` | `zum` |
| 6 | `WIEDER_AB` (unterbrochen) | `zum` |
| 7 | `BEENDET` und `STORNIERT` | – |

Ein Vertrag, der in sechs Wochen ohne Kündigung endet, steht also vor einer Kündigungsfrist in zehn
Monaten. Bei gleichem Rang und Datum entscheidet der angezeigte Name (`bezeichnung`, sonst `anbieter`;
ohne Groß-/Kleinschreibung und Akzente), zuletzt die `id`.

### Vertrag abrufen

`GET /api/vertraege/{id}` → **200** mit dem Vertrag wie in der [Antwort](#antwort) oben, inkl. `version`
für das Ändern und `berechnet` zum heutigen Tag. Unbekannte ID oder Vertrag eines anderen Haushalts →
**404** `NICHT_GEFUNDEN`; ID nicht im UUID-Format → **400** `ANFRAGE_UNGUELTIG`.

### Vertrag ändern

`PUT /api/vertraege/{id}` (CSRF-Token nötig) mit allen Feldern wie beim [Anlegen](#vertrag-anlegen)
plus `version` aus der Antwort (Schema `VertragAenderung`) → **200** mit dem Vertrag wie in der
[Antwort](#antwort), inkl. der neuen `version`:

```json
{ "version": 2, "anbieter": "Musterversicherung AG", "kategorieId": "7cb7a2b8-38a6-432d-8546-03b3d74f76ec", … }
```

Alle Felder werden ersetzt, `preise` und `einmalkosten` als ganze Liste (Konflikte deckt die `version` des Vertrags
ab). Bei `kuendbar: false` gilt dasselbe Leeren wie beim Anlegen.

**Unterbrechungen entfernen:** `unterbrechungIds` (Pflicht, darf leer sein) sind die IDs der
[Unterbrechungen](#wieder-aufnehmen), die bleiben sollen; fehlende werden gelöscht, in derselben Transaktion und mit
derselben `version`. Der Vertrag zählt dort dann als durchgehend gelaufen, die beiden Phasen werden eine, mit
Abbuchungen. Anlegen oder ändern lassen sie sich so nicht, das geht nur über die Wiederaufnahme. Eine Wiederaufnahme
ganz zurücknehmen heißt: Unterbrechung entfernen und `gekuendigtZum` setzen; ein Datum korrigieren: entfernen,
Kündigung setzen, erneut wieder aufnehmen. Zu den verbleibenden Unterbrechungen müssen die Angaben passen:

| Regel | Feld | Wann |
|---|---|---|
| `Unbekannt` | `unterbrechungIds[i]` | der Vertrag hat keine Unterbrechung mit dieser ID |
| `BeginnVorUnterbrechung` | `beginn` | `beginn` liegt nicht vor der ersten Unterbrechung |
| `KuendigungNachUnterbrechung` | `gekuendigtZum` | vor `aktuellerBeginn` (die letzte Phase dauert mindestens einen Tag) |
| `LaufzeitendeNachAktuellemBeginn` | `laufzeitende` | vor `aktuellerBeginn` |

Fehler: **400** `VALIDIERUNG` wie beim Anlegen, zusätzlich `version` und `unterbrechungIds` (`NotNull`); veraltete
`version` → **409** `KONFLIKT`; unbekannte ID oder Vertrag eines anderen Haushalts → **404**
`NICHT_GEFUNDEN`; ID nicht im UUID-Format → **400** `ANFRAGE_UNGUELTIG`.

### Vertrag löschen

`DELETE /api/vertraege/{id}?version=3` (CSRF-Token nötig) → **204** ohne Body. Endgültig, ohne
Papierkorb; wer einen Vertrag behalten, aber nicht mehr zählen will, setzt „Gekündigt“ mit einem
vergangenen Datum (`gekuendigtZum`, Status dann `BEENDET`).

Fehler: veraltete `version` → **409** `KONFLIKT` (der Vertrag bleibt); `version` fehlt oder ist keine
Zahl, ID nicht im UUID-Format → **400** `ANFRAGE_UNGUELTIG`; unbekannte ID oder Vertrag eines anderen
Haushalts → **404** `NICHT_GEFUNDEN`, auch beim zweiten Löschen derselben ID (die SPA kann das als
„schon weg“ behandeln und neu laden).

### Wieder aufnehmen

Ein gekündigter oder beendeter Vertrag lässt sich wieder aufnehmen, z.&nbsp;B. ein Streaming-Abo, das über den Sommer
gekündigt war ([ADR 0023](entscheidungen/0023-unterbrechungen-statt-pausen-status.md)). Statt eines neuen Vertrags
bleibt es derselbe; die Zeit dazwischen ist eine **Unterbrechung**: Darin wird nichts abgebucht, Preise und
Erhöhungen gelten nach Kalender weiter (wer nach einer Preiserhöhung wieder einsteigt, zahlt den neuen Preis).

`POST /api/vertraege/{id}/wiederaufnahme` (CSRF-Token nötig, Schema `VertragWiederaufnahme`) → **200** mit dem
Vertrag wie in der [Antwort](#antwort) (es entsteht kein neuer Vertrag, daher kein 201):

```json
{ "version": 3, "datum": "2026-09-01", "laufzeitende": null, "verlaengerung": "ZAHLUNGSINTERVALL",
  "kuendigungsfristMonate": 0, "betrag": { "wert": 13.99, "waehrung": "EUR" } }
```

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `version` | ja | wie beim [Ändern](#vertrag-ändern) |
| `datum` | ja | erster Tag nach der Unterbrechung; Vorbelegung `berechnet.wiederaufnahme.vorschlag` |
| `laufzeitende` | nein | neue Mindestlaufzeit, wie beim Ändern auch bei nicht kündbaren Verträgen gespeichert |
| `verlaengerung` | bei kündbaren | wie beim Anlegen; bei `kuendbar: false` ignoriert |
| `kuendigungsfristMonate` | nein | 0–24; bei `kuendbar: false` ignoriert |
| `betrag` | nein | neuer Preis ab `datum`, nur wenn er sich ändert |

**Wirkung:**
- Liegt zwischen `vertragsende` + 1 und `datum` − 1 mindestens ein Tag, wird das eine Unterbrechung
  `{ id, von, bis, art: "KUENDIGUNG" }`. Mit `datum` = `berechnet.wiederaufnahme.fruehestens` entsteht keine: Der
  Vertrag läuft nahtlos im alten Takt weiter, `aktuellerBeginn` bleibt `beginn`.
- `gekuendigtZum` wird geleert, `laufzeitende`, `verlaengerung` und `kuendigungsfristMonate` werden übernommen; sie
  zählen ab `aktuellerBeginn`.
- `betrag` wird ein eingetragener Preis ab `datum`. Ein automatischer Preis an diesem Tag
  ([ADR 0022](entscheidungen/0022-automatische-preiserhoehungen-speichern.md), schon gespeichert oder in diesem
  Speichern fällig) wird ersetzt und entsteht nicht wieder ([Gelöschte Stufen bleiben gelöscht](#betrachtet-bis)).
- [Fällige Stufen](#faellige-stufen) werden gespeichert wie beim Ändern, auch die in der Unterbrechung.
- Liegt `datum` in der Zukunft, ist der Vertrag bis dahin `UNTERBROCHEN` (Kündigung `WIEDER_AB`) und zählt wie ein
  beendeter nicht in „Laufende Verträge“ und „Kosten pro Monat“.

**Fehler:**
- **409** `WIEDERAUFNAHME_NICHT_MOEGLICH`: Status nicht `GEKUENDIGT` oder `BEENDET` oder kein `vertragsende`,
  z.&nbsp;B. `AKTIV`, `STORNIERT` oder `UNTERBROCHEN` (also genau, wenn `berechnet.wiederaufnahme` `null` ist).
- **409** `KONFLIKT`: veraltete `version`.
- **400** `VALIDIERUNG`, `version` und `datum` `NotNull`, `PlausiblesDatum`, `Max` usw. wie üblich, außerdem:

  | Regel | Feld | Wann |
  |---|---|---|
  | `NachVertragsende` | `datum` | `datum` ≤ `vertragsende`; `parameter.vertragsende` für den Text |
  | `NotNull` | `verlaengerung` | fehlt bei einem kündbaren Vertrag |
  | `LaufzeitendeBeiEnde` | `verlaengerung` | `KEINE` ohne `laufzeitende` |
  | `LaufzeitendeNachBeginn` | `laufzeitende` | vor `datum` |
  | `GleicheWaehrung` | `betrag.waehrung` | andere Währung als die Preise |
  | `GueltigAbEindeutig` | `betrag` | an `datum` gibt es schon einen eingetragenen Preis |

  Die Regeln am gespeicherten Vertrag prüft das Backend nacheinander und meldet den ersten Verstoß.
- **404** `NICHT_GEFUNDEN`: unbekannte ID oder Vertrag eines anderen Haushalts.

### Vertragsübersicht

`GET /api/vertraege/uebersicht` → **200** mit den Kennzahlen für die Kachel auf der Startseite und den
Kopf der Seite „Verträge“, zum heutigen Tag berechnet:

```json
{
  "laufendeVertraege": 9,
  "kostenProMonat": [ { "wert": 202.08, "waehrung": "EUR" } ],
  "kostenImJahr": [ { "wert": 2398.43, "waehrung": "EUR" } ],
  "kostenImJahrMitPrognose": true,
  "einmalkostenImJahr": [ { "wert": 149.00, "waehrung": "EUR" } ],
  "jahr": 2026,
  "hatAnteile": true,
  "hatNichtBegonnene": false,
  "naechsteKuendigungsfristen": [
    { "id": "…", "anbieter": "Musterversicherung AG", "bezeichnung": "Kfz-Versicherung",
      "betrag": { "wert": 586.80, "waehrung": "EUR" }, "zahlweise": "JAEHRLICH",
      "kuendigenBis": "2026-11-30", "zum": "2026-12-31", "dringend": true }
  ]
}
```

- **Laufend** sind die Verträge mit `berechnet.status` `AKTIV` oder `GEKUENDIGT` (Stand heute): gekündigte zählen
  bis zu ihrem Ende mit, noch nicht begonnene erst ab ihrem Beginn (ein Vertrag ab 01.12. ab dem 01.12.),
  beendete, unterbrochene und stornierte nicht.
- **Kosten:** je Währung ein Eintrag, nach Währungscode sortiert. Geteilte Verträge zählen nur mit ihrem
  `anteilProzent`; der Anteil wird je Vertrag ungerundet angewendet und erst die Summe gerundet, die Summe der
  `berechnet.eigenerAnteil` aus der Liste kann daher um einige Cent abweichen.
  - `kostenProMonat`: über die laufenden Verträge aus den heute gültigen Beträgen, umgerechnet, nicht
    abgebucht. Summiert werden die exakten Jahreswerte (Betrag × 12 ÷ Monate der Zahlweise), der Monatswert
    ist Summe ÷ 12, erst dann gerundet. Die Summe der `kostenProMonat` aus der Liste kann daher um einige Cent
    abweichen ([ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)).
  - `kostenImJahr`: Summe von `berechnet.kostenImJahr` über die laufenden Verträge **und** die im Jahr
    beendeten oder noch nicht begonnenen mit Abbuchungen im Jahr; `jahr` wie dort. Sie passt also nicht zwingend zu
    `laufendeVertraege` und ist auch nicht `kostenProMonat` × 12 (Preisänderungen im Jahr, Rundung). Ohne
    Einmalkosten, mit der Prognose wie am Vertrag.
  - `kostenImJahrMitPrognose`: `true`, wenn ein Vertrag mit `berechnet.kostenImJahrMitPrognose` in `kostenImJahr`
    eingeht („inkl. Prognose“).
  - `einmalkostenImJahr`: Summe der im Kalenderjahr `jahr` fälligen [Einmalkosten](#einmalkosten) über **alle**
    Verträge, auch beendete, noch nicht begonnene und stornierte (eine Gebühr im März zählt, auch wenn der Vertrag im
    Juni endet); leer ohne solche Einträge.
- **`naechsteKuendigungsfristen`:** die fünf ersten Verträge mit `art` `KUENDIGEN_BIS` (auch noch nicht begonnene) in der
  Reihenfolge der [Liste](#verträge-auflisten), nur mit den Angaben für die Kachel (`betrag` = aktueller
  Preis, auch bei geteilten Verträgen voll); `kuendigenBis`,
  `zum` und `dringend` wie in `berechnet.kuendigung`. Endende und monatlich kündbare Verträge stehen
  nicht darin.
- **`hatAnteile`:** `true`, wenn ein Vertrag, der in `kostenProMonat`, `kostenImJahr` oder `einmalkostenImJahr`
  eingeht, ein `anteilProzent` unter 100 hat; die SPA zeigt dann „Bei geteilten Verträgen nur dein Anteil“.
- **`hatNichtBegonnene`:** `true`, wenn ein Vertrag wegen eines späteren Beginns (`NICHT_BEGONNEN`) nicht in
  `kostenProMonat` zählt; die SPA zeigt dann „Ohne Verträge, die erst später beginnen“.
- Ohne laufende Verträge, im Jahr beendete und Einmalkosten im Jahr: `laufendeVertraege` 0, leere Listen,
  `hatAnteile` und `hatNichtBegonnene` `false`.

### Vorschau

`POST /api/vertraege/vorschau` berechnet die Werte für die **Zusammenfassung** und die Statuszeilen des Formulars
„Neuer Vertrag“ bzw. „Vertrag bearbeiten“ („Dein Anteil“, „Prognose“, „Einmalkosten gesamt“), ohne etwas zu
speichern → **200** (ohne `Location`):

```json
{
  "betrag": { "wert": 13.99, "waehrung": "EUR" },
  "preise": [{ "gueltigAb": "2024-11-22", "betrag": { "wert": 13.99, "waehrung": "EUR" }, "art": "AKTUELL", "automatisch": false, "neu": false }],
  "kostenProMonat": { "wert": 13.99, "waehrung": "EUR" },
  "eigenerAnteil": { "betrag": { "wert": 7.00, "waehrung": "EUR" }, "kostenProMonat": { "wert": 7.00, "waehrung": "EUR" } },
  "naechstePreisaenderung": null,
  "prognose": [],
  "einmalkosten": null,
  "kuendigung": { "art": "MONATLICH_KUENDBAR", "kuendigenBis": "2026-11-22", "zum": "2026-11-22", "fristMonate": 0, "dringend": false },
  "vorschlagNaechsteAm": null
}
```

- **Anfrage:** die Formularwerte `preise`, `zahlweise`, `anteilProzent`, `einmalkosten`,
  `preissteigerung`, `preisgarantieBis`, `preissteigerungBetrachtetBis`, `kuendbar`, `beginn`, `laufzeitende`, `verlaengerung`,
  `kuendigungsfristMonate`, `gekuendigtZum` und `unterbrechungen` wie beim [Anlegen](#vertrag-anlegen); weitere Felder ignoriert das
  Backend, die SPA kann also das ganze Formular schicken. `kuendbar` ist Pflicht, alle anderen dürfen fehlen, auch
  `beginn` (ohne Beginn ist der Status `AKTIV`, und ohne Laufzeitende ist kein Vertragsende bestimmbar). Ohne
  `beginn` rechnet die Vorschau ohne Preise, weil der früheste ab dem Beginn gilt und spätere sonst rückwirkend
  gälten. `preissteigerungBetrachtetBis` übernimmt die SPA beim Bearbeiten unverändert aus der Antwort, bei einem
  neuen Vertrag `null`; sonst zeigte die Vorschau gelöschte Stufen wieder als `neu`.
- **`unterbrechungen`:** `[{ von, bis, art }]` (höchstens 100), beim Bearbeiten die verbleibenden aus der Antwort,
  ohne die im Formular zum Entfernen markierten; bei einem neuen Vertrag leer oder weggelassen. Nötig, weil Status
  und Kündigung ab `aktuellerBeginn` zählen. Unvollständige ignoriert die Vorschau. Sie dürfen sich nicht
  überschneiden, zwischen zweien läuft der Vertrag mindestens einen Tag (`UnterbrechungenGetrennt` an
  `unterbrechungen[i].von`); dazu dieselben Regeln wie beim [Ändern](#vertrag-ändern) (`BeginnVorUnterbrechung`,
  `KuendigungNachUnterbrechung`, `LaufzeitendeNachAktuellemBeginn`).
- **Fehlende Werte sind kein Fehler:** Fehlt eine Angabe, ist jeder Wert `null`, der sie braucht (die SPA zeigt
  „Noch offen“); die übrigen werden trotzdem berechnet. **Unvollständige Zeilen** ignoriert die Vorschau: eine
  Preiszeile ohne `gueltigAb` oder `betrag`, Einmalkosten ohne `bezeichnung`, `betrag` oder `faelligAm`, eine Preissteigerung ohne
  `art`, `rhythmus`, `naechsteAm` oder den Wert zur `art` (dann keine Prognose; der Wert der anderen Art zählt
  nicht). Die Regeln über die Liste (`GueltigAbEindeutig`, `PreisNichtVorBeginn`, `ErsterPreisAbBeginn`,
  `GleicheWaehrung`) gelten für die vollständigen Zeilen; `ErsterPreisAbBeginn` also erst mit `beginn` und einer
  vollständigen Zeile.
- **Antwort**, je Wert wie in `berechnet` eines gespeicherten Vertrags ([Antwort](#antwort)); dieselbe Berechnung,
  für dieselben Werte steht nach dem Speichern dasselbe in der Liste:

  | Feld | `null`, wenn |
  |---|---|
  | `betrag` (aktueller Preis) | kein `beginn` oder keine vollständige Preiszeile |
  | `preise` (wie [Preise](#preise) der Antwort, so wie sie nach dem Speichern wären; für die Etiketten der Preisliste im Formular und den Hinweis auf die `neu`-Stufen) | nie; leere Liste ohne `beginn` oder vollständige Preiszeile, unvollständige Zeilen fehlen |
  | `kostenProMonat` | kein Preis oder keine `zahlweise` |
  | `eigenerAnteil` (`{ betrag, kostenProMonat }`; den Anteil am Jahr zeigt das Formular nicht) | kein Preis oder kein Anteil unter 100&nbsp;% |
  | `eigenerAnteil.kostenProMonat` | keine `zahlweise` |
  | `naechstePreisaenderung` | kein Preis oder keine Änderung bekannt |
  | `prognose` | nie; leere Liste ohne oder mit unvollständiger Preissteigerung |
  | `einmalkosten` (Summe) | keine vollständige Zeile; ohne Preis in der Währung der Einmalkosten |
  | `kuendigung` | nie; ohne Angaben z.&nbsp;B. `KEINE_LAUFZEIT`, bei `kuendbar: false` zählen Verlängerung und Frist wie beim Speichern nicht |

  | `vorschlagNaechsteAm` (Vorschlag für `preissteigerung.naechsteAm`: `beginn` plus einen `rhythmus`, ein Monatsletzter bleibt einer: 31.01. vierteljährlich → 30.04.) | kein `beginn` oder kein `preissteigerung.rhythmus` |

  **Preise wie nach dem Speichern:** Die Vorschau ergänzt die [fälligen Stufen](#faellige-stufen), die das Speichern
  anlegen würde, mit `automatisch: true` und `neu: true` (`neu` gibt es nur in der Vorschau); `betrag` usw. rechnen
  mit ihnen. Die SPA schickt die `neu`-Zeilen beim Speichern nicht mit, das Backend legt sie selbst an. Bei den
  übrigen Zeilen übernimmt die Vorschau `automatisch` aus der Anfrage ungeprüft. Ein `betragErrechnet` gibt es in
  der Vorschau nicht, weil sie den Stand nach dem Speichern zeigt.

  `kostenImJahr`, `preisverlauf` und der Anteil am Jahr stehen nicht in der Vorschau. Das Objekt kann später weitere
  Werte bekommen; die SPA liest nur, was sie braucht.
- **Fehler:** Vorhandene Werte prüft die Vorschau wie das Anlegen, nur ohne Pflicht: **400** `VALIDIERUNG` mit
  `felder`, z.&nbsp;B. `LaufzeitendeNachBeginn`, `Max` bei `kuendigungsfristMonate` oder `PositiverBetrag` an
  `preise[i].betrag`. Die Vorschau zeigt also nie Werte, die sich nicht speichern lassen; `PassenderWert` gibt es in
  der Vorschau nicht (eine Regel ohne passenden Wert ist nur unvollständig). Unbekannter Enum-Wert oder kaputtes
  JSON: **400** `ANFRAGE_UNGUELTIG`. Ob ungültige Werte künftig nur die betroffenen Ergebnisse leeren, klärt
  [#79](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/79).
- Die SPA ruft die Vorschau verzögert auf (Debounce), nicht bei jedem Tastendruck; CSRF-Token wie bei allen
  `POST`.
