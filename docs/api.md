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
| `GET /api/vertraege/kategorien`   | `VERTRAGSUEBERSICHT` | Vertragskategorien, siehe [Verträge](#verträge) |
| `POST /api/vertraege`             | `VERTRAGSUEBERSICHT` | Vertrag anlegen, siehe [Verträge](#verträge)    |
| `GET /api/vertraege`              | `VERTRAGSUEBERSICHT` | Verträge auflisten, siehe [Verträge](#verträge) |
| `GET /api/vertraege/{id}`         | `VERTRAGSUEBERSICHT` | Vertrag abrufen, siehe [Verträge](#verträge)    |
| `PUT /api/vertraege/{id}`         | `VERTRAGSUEBERSICHT` | Vertrag ändern, siehe [Verträge](#verträge)     |
| `DELETE /api/vertraege/{id}`      | `VERTRAGSUEBERSICHT` | Vertrag löschen, siehe [Verträge](#verträge)    |
| `GET /api/vertraege/uebersicht`   | `VERTRAGSUEBERSICHT` | Kennzahlen, siehe [Verträge](#verträge)         |
| `POST /api/vertraege/vorschau`    | `VERTRAGSUEBERSICHT` | Kündigungstermin berechnen, siehe [Verträge](#verträge) |
| `/actuator/health`                | öffentlich      | Health Check, inkl. Probes `liveness`/`readiness`|
| `/actuator/health/systemstatus`   | öffentlich      | Status für die Login-Seite: `db`, `keycloak`     |

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
  "betrag": { "wert": 586.80, "waehrung": "EUR" },
  "zahlweise": "JAEHRLICH",
  "abbuchungAb": "2027-01-01",
  "zahlungsweg": "LASTSCHRIFT",
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
| `betrag` | ja | größer 0 (Feldfehler `PositiverBetrag`) |
| `zahlweise` | ja | `MONATLICH`, `VIERTELJAEHRLICH`, `HALBJAEHRLICH`, `JAEHRLICH` |
| `abbuchungAb` | ja | ab diesem Tag wird im Takt der Zahlweise abgebucht; im Formular „Nächste Abbuchung“ (siehe unten) |
| `zahlungsweg` | nein | `LASTSCHRIFT`, `KREDITKARTE`, `PAYPAL`, `UEBERWEISUNG`, `DAUERAUFTRAG` |
| `kuendbar` | ja | `false` z.&nbsp;B. für den Rundfunkbeitrag: `verlaengerung` und `kuendigungsfristMonate` werden nicht gespeichert, `beginn` und `laufzeitende` bleiben (z.&nbsp;B. „seit 2013“) |
| `beginn`, `laufzeitende` | nein | `laufzeitende` nicht vor `beginn` (Feldfehler `LaufzeitendeNachBeginn` an `laufzeitende`) |
| `verlaengerung` | nein | nach dem Laufzeitende: `ZAHLUNGSINTERVALL`, `MONATLICH`, `ZWOELF_MONATE`, `KEINE` |
| `kuendigungsfristMonate` | nein | 0–24 |
| `gekuendigtZum` | nein | gesetzt = gekündigt; ein vergangenes Datum erfasst einen beendeten Vertrag |
| `notiz` | nein | höchstens 1000 Zeichen |

Alle Datumsfelder zwischen `1900-01-01` und `2199-12-31` (Feldfehler `PlausiblesDatum`, siehe
[Geldbeträge und Datum](#geldbeträge-und-datum)). Leere Texte speichert das Backend als `null`. Einen **Status** schickt die SPA nicht mit; er wird
berechnet (siehe unten).

**`abbuchungAb`** ist ein Ankerpunkt im Zahlungsrhythmus, kein Wert, der veraltet: Das Backend zählt
von dort im Takt der Zahlweise weiter (`berechnet.naechsteAbbuchung`). Das Formular nennt das Feld
„Nächste Abbuchung“. Beim Bearbeiten belegt die SPA es mit `berechnet.naechsteAbbuchung` vor (nur wenn
das `null` ist, mit `abbuchungAb`); ändert sich die Zahlweise, muss der Nutzer das Datum im neuen
Rhythmus bestätigen, denn daraus kann das Backend den neuen Rhythmus nicht ableiten.

### Antwort

Die Antwort enthält die gespeicherten Felder wie oben, die Kategorie als Objekt (wie bei den
Kategorien) statt `kategorieId`, dazu `id` und `version`. Was das Backend berechnet, steht in
`berechnet`; das hängt vom heutigen Tag ab und wird beim Ändern nicht zurückgeschickt:

```json
"berechnet": {
  "status": "AKTIV",
  "kostenProMonat": { "wert": 48.90, "waehrung": "EUR" },
  "kostenProJahr": { "wert": 586.80, "waehrung": "EUR" },
  "naechsteAbbuchung": "2027-01-01",
  "kuendigung": { "art": "KUENDIGEN_BIS", "kuendigenBis": "2026-11-30", "zum": "2026-12-31", "fristMonate": 1, "dringend": true }
}
```

- **Status:** `BEENDET`, wenn `gekuendigtZum` vorbei ist oder ein Vertrag mit Verlängerung `KEINE`
  sein Laufzeitende überschritten hat; `GEKUENDIGT`, solange `gekuendigtZum` noch kommt; sonst `AKTIV`.
- **Kosten:** pro Jahr = Betrag × 12 ÷ Monate der Zahlweise, pro Monat = pro Jahr ÷ 12 (kaufmännisch
  gerundet).
- **Nächste Abbuchung:** die erste ab heute im Takt der Zahlweise, gezählt ab `abbuchungAb`;
  `null`, wenn der Vertrag beendet ist oder vorher endet.
- **Kündigung:** `art` ist die erste passende Zeile:

  | Bedingung | `art` | Daten |
  |---|---|---|
  | Status `BEENDET` | `BEENDET` | keine |
  | Status `GEKUENDIGT` | `GEKUENDIGT` | `zum` = `gekuendigtZum` |
  | nicht kündbar | `NICHT_KUENDBAR` | keine |
  | Verlängerung `KEINE` mit Laufzeitende | `ENDET` | `zum` = Laufzeitende |
  | kein Vertragsende bestimmbar | `KEINE_LAUFZEIT` | keine |
  | Laufzeitende vorbei oder leer, Verlängerung um einen Monat (`MONATLICH` oder `ZAHLUNGSINTERVALL` bei monatlicher Zahlweise) | `MONATLICH_KUENDBAR` | `kuendigenBis`, `zum`; nie `dringend` |
  | sonst | `KUENDIGEN_BIS` | `kuendigenBis`, `zum`; `dringend` bei höchstens 45 Tagen bis `kuendigenBis` |

  - **Vertragsende:** das Laufzeitende, so oft um die Verlängerung (Zahlungsintervall, 1 oder 12 Monate)
    verlängert, bis `kuendigenBis` nicht in der Vergangenheit liegt. Ohne Laufzeitende: bei
    `ZAHLUNGSINTERVALL` und `MONATLICH` der Tag vor `abbuchungAb`, bei `ZWOELF_MONATE` ein Jahr
    nach `beginn`; ohne `verlaengerung` gilt nur das Laufzeitende selbst.
  - **`kuendigenBis`** = Vertragsende minus Frist. Endet der Vertrag am Monatsende, ist auch
    `kuendigenBis` ein Monatsende (30.11. mit einem Monat Frist → 31.10.).
  - `fristMonate` ist die hinterlegte Frist (`null`, wenn keine angegeben ist; gerechnet wird dann mit 0).

Fehler: **400** `VALIDIERUNG` mit den Feldfehlern aus der Tabelle (und den üblichen wie `NotBlank`,
`Size`, `Max`).

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
| 6 | `BEENDET` | – |

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

Alle Felder werden ersetzt, auch `abbuchungAb`: Das Backend bucht danach im Takt der (ggf. neuen)
Zahlweise ab diesem Tag ab. Das Formular belegt „Nächste Abbuchung“ daher mit
`berechnet.naechsteAbbuchung` vor (nur wenn das `null` ist, mit `abbuchungAb`) und lässt das Datum
bei einem Wechsel der Zahlweise bestätigen. Bei `kuendbar: false` gilt dasselbe Leeren wie beim
Anlegen.

Fehler: **400** `VALIDIERUNG` wie beim Anlegen, zusätzlich `version` (`NotNull`); veraltete
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

### Vertragsübersicht

`GET /api/vertraege/uebersicht` → **200** mit den Kennzahlen für die Kachel auf der Startseite und den
Kopf der Seite „Verträge“, zum heutigen Tag berechnet:

```json
{
  "laufendeVertraege": 9,
  "kostenProMonat": [ { "wert": 202.08, "waehrung": "EUR" } ],
  "kostenProJahr": [ { "wert": 2424.96, "waehrung": "EUR" } ],
  "naechsteKuendigungsfristen": [
    { "id": "…", "anbieter": "Musterversicherung AG", "bezeichnung": "Kfz-Versicherung",
      "betrag": { "wert": 586.80, "waehrung": "EUR" }, "zahlweise": "JAEHRLICH",
      "kuendigenBis": "2026-11-30", "zum": "2026-12-31", "dringend": true }
  ]
}
```

- **Laufend** sind alle Verträge mit `berechnet.status` ungleich `BEENDET`; gekündigte zählen bis zu
  ihrem Ende mit.
- **Kosten:** Summe über die laufenden Verträge, je Währung ein Eintrag, nach Währungscode sortiert. Es
  sind auf Monat und Jahr umgerechnete Werte, nicht die tatsächlichen Abbuchungen. Summiert werden die
  exakten Jahreswerte, der Monatswert ist Summe ÷ 12, erst dann kaufmännisch gerundet. Die Summe der
  `kostenProMonat` aus der Liste kann daher um einige Cent abweichen.
- **`naechsteKuendigungsfristen`:** die fünf ersten Verträge mit `art` `KUENDIGEN_BIS` in der
  Reihenfolge der [Liste](#verträge-auflisten), nur mit den Angaben für die Kachel; `kuendigenBis`,
  `zum` und `dringend` wie in `berechnet.kuendigung`. Endende und monatlich kündbare Verträge stehen
  nicht darin.
- Ohne laufende Verträge: `laufendeVertraege` 0 und leere Listen.

### Vorschau des Kündigungstermins

`POST /api/vertraege/vorschau` berechnet den „Nächsten Kündigungstermin“ für das Formular „Neuer Vertrag“ bzw.
„Vertrag bearbeiten“, ohne etwas zu speichern → **200** (ohne `Location`):

```json
{ "kuendigung": { "art": "KUENDIGEN_BIS", "kuendigenBis": "2026-11-30", "zum": "2026-12-31", "fristMonate": 1, "dringend": true } }
```

- **Anfrage:** die Formularwerte `zahlweise`, `abbuchungAb`, `kuendbar`, `beginn`, `laufzeitende`,
  `verlaengerung`, `kuendigungsfristMonate` und `gekuendigtZum` wie beim [Anlegen](#vertrag-anlegen); weitere
  Felder ignoriert das Backend, die SPA kann also das ganze Formular schicken. `kuendbar` ist Pflicht, alle
  anderen dürfen fehlen (ergibt z.&nbsp;B. `KEINE_LAUFZEIT`).
- **`kuendigung`** wie `berechnet.kuendigung` in der [Antwort](#antwort): Für dieselben Werte steht nach dem
  Speichern dasselbe in der Liste, auch für eine eingetragene Kündigung (`GEKUENDIGT` bzw. `BEENDET`). Bei
  `kuendbar: false` zählen Verlängerung und Frist wie beim Speichern nicht.
- Das Objekt kann später weitere Werte bekommen; die SPA liest nur, was sie braucht.
- **Fehler:** Die Prüfungen sind dieselben wie beim Anlegen, nur ohne Pflichtfelder: **400** `VALIDIERUNG`
  mit `felder`, z.&nbsp;B. `LaufzeitendeNachBeginn` oder `Max` bei `kuendigungsfristMonate`. Die Vorschau
  zeigt also nie einen Termin für Werte, die sich nicht speichern lassen. Unbekannter Enum-Wert oder kaputtes
  JSON: **400** `ANFRAGE_UNGUELTIG`.
- Die SPA ruft die Vorschau verzögert auf (Debounce), nicht bei jedem Tastendruck; CSRF-Token wie bei allen
  `POST`.
