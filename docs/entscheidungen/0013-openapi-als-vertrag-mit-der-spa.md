# 0013 OpenAPI-Beschreibung als Vertrag mit der SPA

**Status:** angenommen, 2026-11-02, nachgetragen mit
[#122](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/122) (umgesetzt mit
[#50](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/50) und
[#70](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/70))

## Kontext

Die SPA braucht Typen für Anfragen und Antworten. Von Hand gepflegt laufen sie auseinander, sobald sich ein
Feld ändert, und der Fehler fällt erst zur Laufzeit auf. Damit erzeugte Typen stimmen, muss die Beschreibung
genau sagen, welche Felder fehlen oder `null` sein können.

## Entscheidung

Die OpenAPI-Beschreibung, die springdoc aus den Controllern erzeugt, ist der Vertrag zwischen Backend und SPA.

- `OpenApiExportTests` schreibt sie nach `docs/openapi.json`. Die Datei ist eingecheckt, die CI schlägt
  fehl, wenn sie nicht dem Build entspricht.
- Ohne JSpecify-`@Nullable` ist ein Feld nie `null`. In Erfolgsantworten ist jedes Feld `required`, weil
  das Backend auch `null`-Felder ausliefert. `OpenApiExportTests` prüft das, soweit ein Testfall das Feld
  als `null` liefert.
- Die SPA erzeugt daraus ihre Typen (`npm run api:typen`) und checkt sie ein.

## Folgen

- Eine API-Änderung zeigt sich im Diff von `openapi.json` und nach dem Erzeugen als Typfehler in der SPA,
  nicht erst im Betrieb.
- Schemanamen und `operationId` kommen aus DTO- und Methodennamen und müssen in der ganzen Anwendung
  eindeutig sein (Konventionen in [`entwicklung.md`](../entwicklung.md#openapi-beschreibung)).
- Modulspezifische Fehlercodes stehen nicht in der Beschreibung, sondern in [`api.md`](../api.md).
