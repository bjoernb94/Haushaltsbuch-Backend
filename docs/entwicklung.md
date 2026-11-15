# Entwicklung

Konventionen für die Arbeit am Backend: Module, Fachdaten, Datenbank, Tests und Git. Die
Gründe für die wichtigsten Entscheidungen stehen unter [Entscheidungen](entscheidungen/README.md).

## Module

Paketiert nach Fachbereichen, nicht nach technischen Schichten. Jedes direkte Unterpaket von
`de.bochmann.haushaltsbuch` ist ein Spring-Modulith-Modul, deklariert in seiner `package-info.java`
(Übersicht und Diagramm: [README](../README.md#projektstruktur)). Regeln, die `ModularityTests` prüft:

- Nur das Wurzelpaket eines Moduls ist seine öffentliche API; Unterpakete sind intern.
- Keine zyklischen Abhängigkeiten zwischen Modulen.
- `shared` ist offen (`Type.OPEN`) und darf von allen Modulen genutzt werden, selbst aber
  von keinem Fachmodul abhängen. Braucht `shared` Daten aus einem Modul, definiert es eine
  Schnittstelle, die das Modul implementiert (Beispiel: `KomponentenAbfrage`).

`ModularityTests` schreibt außerdem die Modul-Dokumentation nach [`docs/module`](module). Ändern
sich Module oder Abhängigkeiten, gehören die neu erzeugten Dateien mit in den Commit, und das
Mermaid-Diagramm im README wird angepasst. Die GitHub Action schlägt fehl, wenn `docs/module` nicht
dem eingecheckten Stand entspricht.

### Aufbau eines Fachmoduls

Innerhalb eines Fachmoduls wird nach Themen in Unterpakete geteilt, jedes Thema mit allem, was
dazugehört (Entity, Repository, Service, Controller, DTOs). Reine Berechnungen ohne Spring
bekommen ein eigenes Unterpaket. Beispiel `vertraege`:

```
vertraege
├── package-info.java   Modul-Deklaration; im Wurzelpaket nur, was andere Module nutzen dürfen
├── vertrag             Vertrag, VertragRepository, VertragService, VertragController,
│                       VertragAnfrage, VertragAntwort, Zahlungsweg
├── kategorie           VertragKategorie, Repository, Service, Controller
└── berechnung          Vertragsberechnung, Kosten und die Enums, die sie braucht (Zahlweise,
                        Verlaengerung …); ohne Spring, mit Unit-Tests
```

- Klassen, die nur im eigenen Unterpaket gebraucht werden, bleiben package-private; was ein anderes
  Unterpaket braucht, ist `public`. Von außerhalb des Moduls sind Unterpakete trotzdem nicht
  erreichbar, das prüft `ModularityTests`.
- Braucht ein anderes Modul etwas, liegt es im Wurzelpaket (Service-Fassade, Ereignisse). Ist das
  nicht sinnvoll, ein Unterpaket mit `@NamedInterface` freigeben.
- Kleine Module ohne Themen (wie `nutzer`) liegen flach im Wurzelpaket.

## Zugriffsschutz in Modulen

- `SecurityConfig` ordnet die API-Pfade der Fachmodule ihren Komponenten zu (Tabelle in der
  [API](api.md#komponenten)) und prüft sie bei jeder Anfrage über `KomponentenAbfrage`
  (implementiert im Modul `haushalt`). **Neue Endpunkte eines Fachmoduls müssen unter dessen
  API-Pfad liegen**; ein Endpunkt außerhalb ist für jeden angemeldeten Nutzer erreichbar.
- Die Nutzer-ID im Code ist immer die Keycloak-ID (Claim `sub`), als `UUID` ermittelt über
  `AngemeldeterNutzer.id(...)`; Name und E-Mail über `AngemeldeterNutzer.claims(...)` aus dem
  Token der Anmeldung, nicht aus Keycloak.
- Keycloak-Rollen werden derzeit nicht ausgewertet. Werden sie später gebraucht (z.&nbsp;B. für einen
  Admin-Bereich), steht in `SecurityConfig`, wie sie übernommen werden.

## Haushalte

Fachdaten gehören einem **Haushalt** (Modul `haushalt`), nicht einem einzelnen Nutzer. Ein
Haushaltsbuch kann so von mehreren Nutzern gemeinsam geführt werden.

- Tabellen: `haushalt` (`id`, optionaler `name`, `angelegt_am`), `haushalt_mitglied`
  (`haushalt_id`, `nutzer_id`) und `haushalt_komponente`. Vorerst gehört ein Nutzer zu genau
  einem Haushalt (Unique auf `nutzer_id`); das Schema erlaubt mehrere Mitglieder je Haushalt.
- Die Registrierung legt einen Haushalt mit dem Nutzer als einzigem Mitglied und den gewählten
  Komponenten an. Gebuchte Komponenten gelten für den ganzen Haushalt.
- **Fachtabellen speichern `haushalt_id` (UUID), nicht `nutzer_id`**, ohne Fremdschlüssel über
  Modulgrenzen. Daten eines fremden Haushalts liefern **404**, nicht 403, damit keine Rückschlüsse
  auf fremde Daten möglich sind.

### Trennung der Haushalte

Fach-Entities erben von `shared.fachdaten.FachEntity`; deren `haushalt_id` ist eine
Hibernate-`@TenantId`. Hibernate filtert damit **jede** Abfrage (Repository-Methoden, JPQL,
Criteria, `findById`, `deleteById`, Massenlöschen per JPQL) auf den aktuellen Haushalt und setzt
`haushalt_id` beim Anlegen selbst. Fachmodule filtern also nicht selbst; eine fremde ID ist
schlicht „nicht gefunden“ (404).

- **Aktueller Haushalt** (`HaushaltKontext`): im Request der Haushalt des angemeldeten Nutzers,
  einmal je Request ermittelt (`HaushaltAbfrage`, implementiert im Modul `haushalt` per JDBC).
- **Ohne Request** (Listener, Jobs): `HaushaltKontext.fuer(haushaltId, () -> …)`; die Transaktion
  muss darin beginnen.
- **Ohne Haushalt**: Platzhalter `HaushaltKontext.KEIN_HAUSHALT`, weil Hibernate für jede Session
  einen Wert verlangt. Abfragen auf Fachdaten liefern dann nichts; Speichern schlägt fehl. Das
  betrifft anonyme Requests (auch die Registrierung, die den Haushalt erst anlegt) und Code ohne
  Request und ohne `fuer(...)`. Registrierte Nutzer haben immer einen Haushalt; nur Konten, die
  direkt in Keycloak angelegt wurden, fallen zur Sicherheit ebenfalls darunter.
- **Kindtabellen** einer Fach-Entity (`@ElementCollection`, z.&nbsp;B. `vertrag_preis`, `vertrag_einmalkosten`) haben keine eigene
  `haushalt_id`: Hibernate lädt sie nur über ihre Entity, die schon gefiltert ist. Native SQL auf ihnen muss
  über die Tabelle der Entity einschränken.
- **Nicht gefiltert:** native SQL-Abfragen und Tabellen ohne `FachEntity`. Tabellen mit gemischten
  Zeilen (z.&nbsp;B. Standardkategorien mit `haushalt_id IS NULL` und eigene eines Haushalts) nutzen
  kein `@TenantId` und filtern selbst mit `HaushaltKontext.id()`.
- **Pflicht-Test je Fachmodul:** Daten als Haushalt A anlegen, als Haushalt B abrufen, ändern und
  löschen → 404. Testbaustein: `TestHaushalt.anlegen(haushaltService, …)` mit `anmeldung()`.

Weitere Mitglieder einladen:
[#15](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/15).

## Fachendpunkte und Fach-Entities

Das Format der Endpunkte für die SPA steht in der [API](api.md#fachendpunkte). Im Code gilt:

- **DTOs:** Controller nehmen und liefern Records (z.&nbsp;B. `VertragAnfrage`, `VertragAntwort`), nie
  Entities. So gelangen interne Felder (`haushalt_id`, Audit-Spalten) nicht in die API, und
  berechnete Felder haben einen Platz. Je Fachobjekt: `<Objekt>Anfrage` zum Anlegen,
  `<Objekt>Aenderung` zum Ändern (dieselben Felder plus `@NotNull Long version`, damit `version` in der
  OpenAPI Pflicht ist und beim Anlegen fehlt) und `<Objekt>Antwort`. Ein gemeinsames Interface (z.&nbsp;B.
  `Vertragsangaben`) lässt Entity und klassenweite Validatoren mit beiden arbeiten; ein Test hält die
  beiden Feldlisten gleich (`VertragAnfragenTests`). Eine Vorschau ohne Speichern (z.&nbsp;B.
  `VertragVorschauAnfrage`) nimmt nur die Felder der Berechnung, über ein kleineres Interface
  (`Berechnungsangaben`), das auch die Bereinigung vor der Berechnung enthält; so rechnen Vorschau und
  gespeicherter Vertrag gleich. Ihre Prüfungen gleichen denen beim Anlegen, nur ohne `NotNull` (Test wie oben).
- **Fach-Entities** erben von `shared.fachdaten.FachEntity`: UUID, Haushalt (siehe
  [Trennung der Haushalte](#trennung-der-haushalte)), `@Version` und Audit-Spalten (Spalten siehe
  [Datenbank](#datenbank)).
- **Version:** DTOs liefern `version` aus. Beim `PUT` ruft der Service
  `entity.pruefeVersion(dto.version())`, bevor er Felder ändert, beim `DELETE` mit dem Parameter
  `@RequestParam long version`, bevor er löscht (erst `findById`, dann `delete`, damit Unbekanntes 404 ergibt); eine veraltete Version wirft
  `KonfliktException` (409 `KONFLIKT`). Gleichzeitige Speichervorgänge bildet die
  Fehlerbehandlung ebenfalls auf 409 ab. Hibernate erhöht die Version erst beim Schreiben, daher
  vor dem Bauen der Antwort `repository.flush()`, sonst liefert sie die alte Version und das nächste
  `PUT` ergibt 409.
- **Paging** über `Pageable` und `Page`; die Serialisierung als stabiles DTO stellt `FachdatenConfig` ein.

### OpenAPI-Beschreibung

springdoc erzeugt die Beschreibung aus den Controllern; `OpenApiExportTests` schreibt sie nach
[`docs/openapi.json`](openapi.json). Die Datei ist eingecheckt wie `docs/module`: Ändert sich die API,
gehört sie mit in den Commit, und die GitHub Action schlägt fehl, wenn sie nicht dem Build entspricht.

- **Automatisch** (`shared.openapi.OpenApiConfig`): Anmeldung (Session-Cookie, bei schreibenden
  Anfragen zusätzlich der CSRF-Header) und die allgemeinen Fehlerantworten als Schema `Problem`: 400
  bei Request-Body, 401/403 bei geschützten Endpunkten, 404 bei Pfaden mit ID. Dafür keine
  Annotationen an den Endpunkten.
- **`null`:** Jedes Feld eines DTOs, das `null` sein kann, bekommt JSpecify-`@Nullable` (direkt vor dem
  Typ, in Anfragen und Antworten). Fehlt es, ist das Feld nie `null`; springdoc beschreibt die Felder
  entsprechend. In Erfolgsantworten ist zusätzlich jedes Feld Pflicht (`OpenApiConfig`), weil das Backend
  auch `null`-Felder ausliefert. `OpenApiExportTests` prüft das und schlägt bei einem vergessenen
  `@Nullable` fehl, soweit ein Testfall das Feld als `null` liefert.
- **Enums** werden automatisch eigene Schemas, ohne Annotationen; der Enum-Name wird wie ein DTO-Name zum
  Schemanamen.
- **Je Controller:** `@Tag(name = "…")` mit dem Fachbereich (z.&nbsp;B. `Verträge`), damit erzeugte
  Clients sinnvoll gruppiert sind. Öffentliche Endpunkte zusätzlich `@SecurityRequirements` ohne
  Inhalt.
- **`POST` mit `ResponseEntity.created(...)`:** zusätzlich `@ResponseStatus(HttpStatus.CREATED)` an die
  Methode, sonst steht in der Beschreibung 200 statt 201.
- **DTO-Namen** werden zu Schemanamen, ohne Paket: in der ganzen Anwendung eindeutig wählen
  (`VertragKategorieAntwort`, nicht `KategorieAntwort`), sonst überschreibt ein Schema still das andere.
- **Methodennamen** der Controller werden zur `operationId` (z.&nbsp;B. `vertragAnlegen`) und damit zu
  Funktionsnamen in erzeugten Clients: sprechend und in der ganzen Anwendung eindeutig wählen.
- Modulspezifische Fehlercodes stehen nur in [`api.md`](api.md#fehlerantworten), nicht in der
  OpenAPI-Beschreibung.

## Fehler im Code

Das Format für die SPA steht unter [Fehlerantworten](api.md#fehlerantworten).

- Allgemeine Fehler beantwortet `shared.fehler.FehlerBehandlung` zentral (Validierung, Fehler von
  Spring MVC, 401/403 der Security-Filterkette, unerwartete Fehler). Module werfen dafür
  z.&nbsp;B. `NichtGefundenException` oder `KonfliktException`.
- Modulspezifische Fehler beantworten die Controller des Moduls per `@ExceptionHandler` mit
  `Probleme.problem(status, code, detail)` und einem eigenen Code (`GROSSBUCHSTABEN_MIT_UNTERSTRICH`,
  deutsch). Neue Codes gehören in die Tabelle der [API](api.md#fehlerantworten) bzw. zum Endpunkt.
- **Fachliche Prüfungen im Service**, die Bean Validation nicht leisten kann (z.&nbsp;B. unbekannte
  oder fremde Kategorie), werfen `shared.fehler.UngueltigeEingabeException(feld, code, detail)`. Die
  Antwort hat dasselbe Format wie bei `@Valid`; `code` ist ein Name in PascalCase (`Unbekannt`).
- **Regeln über mehrere Felder** (z.&nbsp;B. Laufzeitende nach Beginn) sind eigene Constraints auf dem
  Record. Der Validator hängt den Verstoß an das Feld, das die SPA markieren soll
  (`disableDefaultConstraintViolation()`, `buildConstraintViolationWithTemplate(...).addPropertyNode("laufzeitende")`);
  sonst ist `feld` leer. `code` ist der Name der Annotation, z.&nbsp;B. `LaufzeitendeNachBeginn`.
  Beispiel: `FehlerBehandlungTests`.
- `detail` ist englisch und für Entwickler; Texte für Nutzer bildet die SPA aus `code`.

## Geldbeträge und Datum im Code

- Geldbeträge sind immer `shared.geld.Betrag` (Wert und `Currency`, `@Embedded`). Request-DTOs
  markieren sie mit `@NotNull @Valid`. Nie eine einzige Währung annehmen: Rechnen nur innerhalb
  einer Währung, Summen nach Währung gruppieren. Gerechnet wird ungerundet, gerundet (`HALF_UP`) erst,
  wo ein Betrag entsteht oder ausgegeben wird; `Betrag.mal`/`geteilt` runden sofort und taugen nur für
  den letzten Schritt ([ADR 0011](entscheidungen/0011-rundung-von-geldbetraegen.md)).
- Fachliche Daten sind `LocalDate`. „Heute“ ist `LocalDate.now(clock)` mit dem `Clock`-Bean
  (Europe/Berlin, `shared.zeit`), damit Tests das Datum festlegen können. Zeitstempel sind `Instant`.
  Datumsfelder in Anfrage-DTOs bekommen `@PlausiblesDatum` (1900–2199): Extremwerte ergäben sonst beim
  Weiterzählen einen Überlauf oder einen Datenbankfehler, also 500 statt 400.
- Berechnungen gehören ins Backend, die SPA formatiert nur ([ADR 0010](entscheidungen/0010-berechnungen-nur-im-backend.md)).
- Spalten dafür: siehe [Datenbank](#datenbank).

## Datenbank

Das Datenbankschema wird ausschließlich über Flyway (`src/main/resources/db/migration`)
gepflegt; Hibernate prüft es nur (`ddl-auto: validate`). Jede neue Entity braucht daher eine
Migration, sonst startet die Anwendung nicht.

Namenskonvention: `V<n>__<modul>_<beschreibung>.sql` mit der nächsten freien Nummer, z.&nbsp;B.
`V<n>__vertraege_vertrag.sql`.
Bereits ausgelieferte Migrationen nicht mehr ändern, sondern eine neue anlegen.

Spalten für Geldbeträge und Datum:

| Java                       | Spalte(n)                                                |
|----------------------------|----------------------------------------------------------|
| `Betrag preis`             | `preis_wert NUMERIC(19, 2)`, `preis_waehrung VARCHAR(3)` |
| `LocalDate buchungstag`    | `DATE`                                                   |
| `Instant angelegtAm`       | `TIMESTAMPTZ`                                            |
| Enum `Zahlweise zahlweise` | `zahlweise VARCHAR(40)`, `@Enumerated(EnumType.STRING)`  |

Enums werden als Name gespeichert, ohne `CHECK`-Constraint: Maßgeblich ist das Java-Enum, neue
Werte brauchen so keine Migration. Werte nicht umbenennen oder entfernen, ohne die Daten per
Migration anzupassen.

Die Namen eingebetteter Werte bildet Hibernate aus Feldname und Komponente
(`ImplicitNamingStrategyComponentPathImpl`), daher passen mehrere Beträge in eine Tabelle.

Migrationen nur für Tests liegen in `src/test/resources/db/migration` mit Versionen ab 1000.

Fach-Entities erben von `shared.fachdaten.FachEntity` (ID, Haushalt, `@Version`, Audit-Spalten) und brauchen in
ihrer Tabelle diese Spalten:

```sql
id            UUID PRIMARY KEY,
haushalt_id   UUID        NOT NULL,  -- mit Index, siehe Trennung der Haushalte
version       BIGINT      NOT NULL,
angelegt_am   TIMESTAMPTZ NOT NULL,
angelegt_von  UUID,
geaendert_am  TIMESTAMPTZ NOT NULL,
geaendert_von UUID
```

Spring Data füllt die Audit-Spalten automatisch; `*_von` ist die Keycloak-ID des angemeldeten
Nutzers (leer ohne Anmeldung). Sie werden gespeichert, aber noch nicht ausgeliefert
([#42](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/42)).

Die Tabelle `nutzer` speichert nur die Keycloak-ID; E-Mail, Name und Passwort liegen
ausschließlich in Keycloak, die gebuchten Komponenten beim [Haushalt](#haushalte).

## Build und Tests

```bash
./mvnw verify                                     # Build inkl. aller Tests
./mvnw test -Dtest=ModularityTests                # einzelne Testklasse
./mvnw test -Dtest=SecurityConfigTests#healthIsPublic   # einzelne Testmethode
./mvnw spring-boot:build-image -DskipTests        # Container-Image bauen
```

Integrationstests starten PostgreSQL per Testcontainers und benötigen daher eine laufende
Container-Umgebung. Ein Container wird für den ganzen Testlauf geteilt. Keycloak wird in den
Tests nicht gebraucht: Die Tests simulieren die Session-Anmeldung mit `oidcLogin()`
(Fachmodule: `TestHaushalt.anmeldung()`), alle Zugriffe auf Keycloak sind Mocks. `jwt()` passt
nicht, weil das Backend keine Bearer-Tokens annimmt. `ModularityTests` prüft die Modulstruktur
mit Spring Modulith und schreibt die [Modul-Dokumentation](#module).

### Aufbau der Tests

Jede abweichende Konfiguration (eigene `@MockitoBean`s, Properties, Profile) erzeugt einen
eigenen Spring-Kontext und verlangsamt den Build. Daher gibt es zwei Annotationen
(unter `src/test`, Paket `de.bochmann.haushaltsbuch`):

| Annotation         | Wofür                                              | Kontext                         |
|--------------------|----------------------------------------------------|---------------------------------|
| `@IntegrationTest` | Web- und Security-Tests (MockMvc, Filterkette), modulübergreifende Abläufe | ganze Anwendung, ein gemeinsamer Kontext |
| `@ModuleTest`      | Fachlogik eines Moduls (Services, Repositories)    | nur das Modul der Testklasse und `shared` (`@ApplicationModuleTest`), einer je Modul |
| `@FachmodulTest`   | wie `@ModuleTest`, für Fachmodule mit Fach-Entities | wie `@ModuleTest`, Haushalt-Schnittstellen als Mocks |

`@IntegrationTest` setzt Profil `dev`, Testcontainers und MockMvc und ersetzt alle
Keycloak-Zugriffe:

- `Identitaetsanbieter` (Admin API) und `KeycloakTokenClient` (Token-Endpunkt) sind Mocks,
  `Anmeldung` ist ein Spy (arbeitet echt gegen den gemockten Token-Endpunkt, lässt sich aber
  mit `verify`/`doThrow` steuern).
- Der Health-Indikator `keycloakHealthIndicator` ist ein Mock und meldet vor jedem Test `UP`.

Tests holen sich die Mocks per `@Autowired` und legen ihr Verhalten mit `when(...)` fest; Spring
setzt sie nach jedem Test zurück. Eigene `@MockitoBean`s nur in begründeten Ausnahmen, ebenso
`@DirtiesContext` (Beispiel: `CsrfCookieTests`, das einen frischen Kontext braucht).

`@ModuleTest` startet nur die Beans des eigenen Moduls und von `shared`. `shared` ist ausdrücklich
eingebunden (`extraIncludes`), weil Spring Modulith sonst alle Beans außerhalb des Moduls entfernt,
auch `FachdatenConfig` (Auditing, Trennung der Haushalte) und das `Clock`-Bean. Greift das Modul auf
Beans anderer Module zu, schlägt der Start fehl; diese dann in der Testklasse mit `@MockitoBean`
ersetzen. Spring Modulith legt sonst eine Testinstanz für alle Tests einer Klasse an
(`@TestInstance(PER_CLASS)`); `@ModuleTest` stellt wieder eine je Testmethode ein, Felder wie
`private final UUID haushalt = UUID.randomUUID()` sind also in jedem Test neu.

Fachmodule testen ihre Services und Repositories mit `@FachmodulTest`. `shared` braucht
`HaushaltAbfrage` und `KomponentenAbfrage`, die das Modul `haushalt` implementiert; dort sind beide
Mocks. Den Haushalt setzt der Test selbst, und die Transaktion beginnt darin, daher Testklasse und
-methode nicht `@Transactional`:

```java
@FachmodulTest
class VertragServiceTests {

	@Test
	void legtVertragAn() {
		var vertrag = HaushaltKontext.fuer(UUID.randomUUID(), () -> vertragService.anlegen(anfrage));
		…
	}

}
```

Endpunkte, Security und der Pflicht-Test zur Trennung der Haushalte laufen weiter mit
`@IntegrationTest`. Reine Berechnungen (z.&nbsp;B. Kündigungstermine) sind Klassen ohne Spring und
bekommen Unit-Tests mit fester `Clock` bzw. festem Datum.
Testdaten anderer Module in einem `@IntegrationTest` über deren öffentliche API anlegen
(z.&nbsp;B. `HaushaltService.anlegen(...)`), nicht über Mocks.

Unit-Tests ohne Spring (z.&nbsp;B. `KeycloakTokenClientTests`) brauchen keine der Annotationen.

Zum lokalen Start mit Testcontainers statt Docker Compose:
`TestHaushaltsbuchBackendApplication` (unter `src/test`) ausführen.

Die GitHub Action `.github/workflows/build.yml` führt bei jedem Push auf `main` und bei jedem
Pull Request `./mvnw verify` aus und lädt bei Fehlern die Testberichte hoch.

Dependabot (`.github/dependabot.yml`) öffnet Pull Requests für neue Versionen: Maven wöchentlich,
GitHub Actions und die Images aus `compose.yaml` monatlich. Major-Updates von PostgreSQL sind
ausgenommen, weil sie eine Datenmigration brauchen. Keycloak-Updates vor dem Merge lokal mit
Registrierung und Anmeldung testen; die Tests im Build laufen ohne Keycloak.

## Testkonten

Für Prüfungen von Hand gegen das echte Backend legt ein Skript feste Konten mit passenden Verträgen an.
Backend und Keycloak müssen laufen, das Backend mit dem aktuellen Stand:

```bash
python3 scripts/testdaten.py                              # gegen http://localhost:8080
python3 scripts/testdaten.py --url http://localhost:8081  # anderer lokaler Port
```

Es braucht nur Python 3 ohne Zusatzpakete und arbeitet über die öffentliche API. Beliebig oft ausführbar:
Vorhandene Konten bleiben, ihre Verträge werden ersetzt (die IDs ändern sich also). Die Daten hängen vom
heutigen Tag ab; erneutes Ausführen bringt den Stand wieder in Ordnung. Die E2E-Tests der SPA nutzen diese
Konten nicht.

| Konto | Komponenten | Verträge |
|---|---|---|
| `testkonto.alles@example.org` | alle vier | alle Fälle unten, mehr als fünf anstehende Fristen |
| `testkonto.leer@example.org` | `VERTRAGSUEBERSICHT` | keine |
| `testkonto.ohne-fristen@example.org` | `VERTRAGSUEBERSICHT` | nur Verträge ohne anstehende Frist |
| `testkonto.ohne-vertragsbereich@example.org` | `PORTFOLIO` | keine |

Passwort aller Konten: `Testkonto-1!`. Es gilt nur lokal und darf nirgends sonst verwendet werden. Das Skript
bricht ab, wenn der Host nicht `localhost`, `127.0.0.1` oder `::1` ist oder `/v3/api-docs` nicht antwortet (nur
im dev-Profil). Ein zweites Konto zeigt auch, dass Verträge eines fremden Haushalts mit 404 antworten.

**Fälle im Konto `alles`.** Der Fall steht jeweils in `anbieter`, die einzelnen Verträge nur im Skript:

- **Kündigungsart:** `KUENDIGEN_BIS` (dringend, nicht dringend), `MONATLICH_KUENDBAR`, `ENDET`,
  `KEINE_LAUFZEIT` (ohne Laufzeit, Laufzeitende ohne Verlängerung mit abgelaufener Frist), `NICHT_KUENDBAR`,
  `GEKUENDIGT`, `BEENDET` (`gekuendigtZum` vorbei, Verlängerung `KEINE` mit vergangenem Laufzeitende), `STORNIERT`.
- **Status:** `NICHT_BEGONNEN` (kündbar und schon gekündigt), `STORNIERT` (vor dem Beginn gekündigt, mit
  Einmalkosten), dazu `AKTIV`, `GEKUENDIGT` und `BEENDET`.
- **Felder:** alle Zahlweisen, Kategorien und Zahlungswege; Frist keine, 0, 1 und 3 Monate; mit und ohne
  Bezeichnung; Höchstlängen; optionale Felder alle gefüllt und alle leer.
- **Beginn als Anker:** Beginn am 31. (Abbuchungen am 31. bzw. Monatsletzten; monatlich kündbar zum 30., im
  Februar zum Monatsletzten), Beginn heute (Kosten im Jahr nur ab heute). Ohne eigenen Beginn liegt er ein Jahr
  zurück im Raster von heute + 9 Tagen.
- **Anteil:** geteilte Verträge mit 50&nbsp;% und 33,3&nbsp;% sowie einer mit 100&nbsp;% (zählt wie ohne Anteil).
- **Einmalkosten:** im laufenden Jahr und im Vorjahr, einmal bei einem geteilten Vertrag.
- **Preissteigerung:** in Prozent und als Betrag, mit Preisgarantie und mit vergangener „nächster Erhöhung“; einmal
  ohne Erinnerung ans Sonderkündigungsrecht.

Für jeden Fall prüft das Skript `art`, `status`, `dringend`, `vertragsende`, wo angegeben `kuendigung.zum` und
`kostenImJahr`, `berechnet.einmalkosten`, die drei Prognosepreise samt nächster Preisänderung und ob es
`eigenerAnteil` gibt, in der Übersicht `hatAnteile`, `hatNichtBegonnene` und `einmalkostenImJahr`, und bricht bei
einer Abweichung ab,
ebenso wenn eine Feldvariante fehlt oder ein vorhandenes Konto andere Komponenten hat. Am Ende gibt es je Konto
eine Tabelle aus. Es läuft nicht in der CI, weil es ein echtes Keycloak braucht.

**Nicht abgedeckt**, weil sich das nicht mit Testdaten herstellen lässt: Datenbank, Keycloak oder Backend nicht
erreichbar; Komponente zur Laufzeit abbestellt ([#3](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/3));
falsches Passwort, Feldfehler, Konflikt 409; gesperrtes Konto und Keycloak-Pflichtschritte; Fremdwährungen
([#30](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/30)).

## Arbeitsweise mit Git

Auf `main` wird nicht direkt committet. Jede Änderung entsteht auf einem eigenen Branch und
kommt per Pull Request nach `main`, sobald der Build grün ist.

- Branch-Namen: `feature/<kurzname>` für neue Funktionen, `fix/<kurzname>` für Fehlerbehebungen,
  `docs/<kurzname>` für reine Doku-Änderungen, `chore/<kurzname>` für Wartung (Abhängigkeiten,
  Konfiguration, Umbauten ohne neue Funktion). Gehört der Branch zu einem Issue, die Nummer
  voranstellen, z.&nbsp;B. `feature/14-haushalt`.
- Im Pull Request das Issue mit `Closes #<nummer>` verknüpfen, damit es beim Merge geschlossen wird.
- Die PR-Vorlage (`.github/pull_request_template.md`) erinnert an Tests, Doku und ADRs.
- Branches nach dem Merge löschen.

## IntelliJ Run Configurations

Unter `.run/` liegen gemeinsame Run Configurations (Spring-Boot-Start, „Umgebung starten“ und
„Umgebung stoppen“ für PostgreSQL und Keycloak aus `compose.yaml` sowie Maven-Aufrufe für Build, Tests,
Dependency-Checks, Image-Build und Flyway). „Run Backend“ startet die Umgebung nicht mit, damit sich das
Backend neu starten lässt, ohne dass die Datenbank neu startet. Die Konfigurationen enthalten die lokalen
Dev-Werte (DB `haushaltsbuch` auf `localhost:5432`, Podman-Socket unter `/run/user/1000`) und müssen auf
anderen Rechnern ggf. angepasst werden. `flyway:info` / `flyway:validate` arbeiten direkt gegen die
lokale Datenbank; die Migration selbst führt Spring Boot beim Start aus.

„Run Backend“ führt vor dem Start `generate-resources` mit Maven aus. IntelliJs eigener Build
erzeugt `META-INF/build-info.properties` und `git.properties` nicht, ohne sie nennt
`/actuator/info` weder Version noch Commit.
