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
| [0011](0011-rundung-von-geldbetraegen.md)            | Ungerundet rechnen, erst bei der Ausgabe runden         | angenommen |
| [0012](0012-konflikterkennung-per-version.md)        | Konflikterkennung per `version`, auch beim Löschen      | angenommen |
| [0013](0013-openapi-als-vertrag-mit-der-spa.md)      | OpenAPI-Beschreibung als Vertrag mit der SPA            | angenommen |
| [0014](0014-abgeleitete-werte-nicht-speichern.md)    | Abgeleitete Werte nicht speichern                       | angenommen |
| [0015](0015-release-prozess.md)                      | Release nur per Tag mit passendem Milestone             | angenommen |
| [0016](0016-oberflaeche-ohne-ui-bibliothek.md)       | Eigene Oberfläche ohne UI-Bibliothek (Frontend)         | angenommen |
| [0017](0017-standalone-signals-onpush-zoneless.md)   | Standalone, Signals, `OnPush` und zoneless (Frontend)   | angenommen |
| [0018](0018-services-halten-keine-fachdaten.md)      | Services halten keine Fachdaten (Frontend)              | angenommen |
| [0019](0019-auslieferung-ueber-nginx.md)             | Auslieferung über nginx auf demselben Origin (Frontend) | angenommen |
| [0020](0020-design-zuerst.md)                        | Design zuerst: Der Canvas ist maßgeblich (Frontend)     | angenommen |
| [0021](0021-doku-im-repo.md)                         | Dokumentation im Repo, kein Wiki                        | angenommen |
| [0022](0022-automatische-preiserhoehungen-speichern.md) | Automatische Preiserhöhungen als Preise speichern     | angenommen |

Vorlage für neue Einträge: Datei `NNNN-kurzname.md` mit den Abschnitten *Kontext*,
*Entscheidung* und *Folgen*, Status mit Datum und Issue/PR.
