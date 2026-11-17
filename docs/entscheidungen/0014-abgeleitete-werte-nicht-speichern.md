# 0014 Abgeleitete Werte nicht speichern

**Status:** angenommen, 2026-11-01, nachgetragen mit
[#122](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/122) (ergänzt ADR
[0010](0010-berechnungen-nur-im-backend.md))

## Kontext

Viele Werte eines Vertrags folgen aus anderen: Status und Kündigungstermin aus Laufzeit und Frist, der
aktuelle Preis aus der Preisliste, Kosten pro Monat und Jahr, eigener Anteil und Prognose. Einige davon
hängen vom heutigen Datum ab. Gespeichert wären sie morgen falsch und müssten bei jeder Änderung der
Regeln oder Eingaben nachgezogen werden.

## Entscheidung

Gespeichert werden nur Angaben des Nutzers. Abgeleitete Werte berechnet das Backend beim Lesen
(ADR [0010](0010-berechnungen-nur-im-backend.md)) und liefert sie in der Antwort mit, beim Vertrag unter
`berechnet` und als `betrag` (aktueller Preis).

## Folgen

- Nichts veraltet: Ein Statuswechsel um Mitternacht oder eine neue Regel wirkt sofort, ohne Migration oder
  nächtlichen Job.
- Jedes Lesen rechnet. Bei den Datenmengen eines Haushalts ist das unkritisch.
- Sortieren und Filtern nach berechneten Werten geschieht in Java, nicht in SQL. Werden Listen so groß, dass
  das nicht mehr reicht, braucht es einen neuen Eintrag.
- Die SPA schickt berechnete Werte nie zurück; das Backend ignoriert sie.
