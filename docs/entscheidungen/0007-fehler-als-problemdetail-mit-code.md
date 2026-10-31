# 0007 Fehler als `ProblemDetail` mit stabilem Code

**Status:** angenommen, 2026-10-31 ([#11](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/11), PR [#24](https://github.com/bjoernb94/Haushaltsbuch-Backend/pull/24))

## Kontext

Fehler kamen in zwei Formaten (Standardformat von Spring Boot und `ProblemDetail`). Die SPA zeigt
Texte in der Sprache des Nutzers an und braucht dafür eine verlässliche Kennung statt englischer
Meldungen.

## Entscheidung

Alle Fehler kommen als `ProblemDetail` (RFC 9457) mit der Property `code`, einem stabilen,
deutschen Code (`NICHT_GEFUNDEN`, `EMAIL_BEREITS_VERGEBEN` …). `detail` ist englisch und nur für
Entwickler. Validierungsfehler listen in `felder` je Verstoß Feld, Code und Parameter; fachliche
Prüfungen im Service nutzen dasselbe Format. `FehlerBehandlung` in `shared` beantwortet allgemeine
Fehler zentral, modulspezifische bleiben beim Controller des Moduls.

## Folgen

- Die SPA übersetzt anhand von `code`; Codes dürfen sich nicht ändern und sind dokumentiert.
- Neue Fehlerarten brauchen einen Code und einen Eintrag in der Dokumentation.
