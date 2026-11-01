# Entscheidungen

Architekturentscheidungen (ADRs) mit Kontext, Entscheidung und Folgen. Eine Entscheidung wird
nicht nachträglich umgeschrieben: Ändert sie sich, kommt ein neuer Eintrag dazu, und der alte
bekommt den Status *ersetzt durch …*.

| Nr.                                                  | Entscheidung                                            | Status     |
|------------------------------------------------------|---------------------------------------------------------|------------|
| [0001](0001-modularer-monolith.md)                   | Modularer Monolith mit Spring Modulith                  | angenommen |
| [0002](0002-anmeldung-ueber-backend.md)              | Anmeldung über das Backend mit Password Grant           | angenommen |
| [0003](0003-komponenten-in-eigener-datenbank.md)     | Gebuchte Komponenten in der eigenen Datenbank           | angenommen |
| [0004](0004-fachdaten-gehoeren-dem-haushalt.md)      | Fachdaten gehören dem Haushalt                          | angenommen |
| [0005](0005-keine-fremdschluessel-ueber-module.md)   | Keine Fremdschlüssel über Modulgrenzen                  | angenommen |
| [0006](0006-haushaltstrennung-per-tenantid.md)       | Trennung der Haushalte per Hibernate-`@TenantId`        | angenommen |
| [0007](0007-fehler-als-problemdetail-mit-code.md)    | Fehler als `ProblemDetail` mit stabilem Code            | angenommen |
| [0008](0008-modultests-mit-shared.md)                | Modultests binden `shared` ein                          | angenommen |
| [0009](0009-nur-session-anmeldung.md)                | Nur Session-Anmeldung, keine Bearer-Tokens              | angenommen |
| [0010](0010-berechnungen-nur-im-backend.md)          | Fachliche Berechnungen nur im Backend                   | angenommen |

Vorlage für neue Einträge: Datei `NNNN-kurzname.md` mit den Abschnitten *Kontext*,
*Entscheidung* und *Folgen*, Status mit Datum und Issue/PR.
