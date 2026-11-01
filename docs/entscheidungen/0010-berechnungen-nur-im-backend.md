# 0010 Fachliche Berechnungen nur im Backend

**Status:** angenommen, 2026-11-01 ([#66](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/66); umgesetzt
mit [#36](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/36),
[#40](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/40) und
[#41](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/41))

## Kontext

Viele Anzeigen beruhen auf Berechnungen: Status und Kündigungstermin eines Vertrags, nächste Abbuchung,
Kosten pro Monat und Jahr, Summen je Währung, die Sortierung nach Dringlichkeit. Rechnet die SPA einen
Teil davon selbst, gibt es die Regeln zweimal, und sie laufen auseinander. Beim Abgleich des Designs fiel
genau das auf: Das Design hielt beim Verschieben um Monate das Monatsende nicht und zeigte „kündigen bis
30.03.“, das Backend 31.03.

## Entscheidung

Fachliche Berechnungen macht nur das Backend, in Spring-freien Paketen je Modul (z.&nbsp;B.
`vertraege.berechnung`) mit Unit-Tests. Die SPA formatiert nur (Beträge, Datum, Texte zu Codes wie
`kuendigung.art`) und rechnet nichts nach.

- Antworten liefern Berechnetes mit, z.&nbsp;B. `berechnet` am Vertrag; die SPA schickt es nie zurück.
- Listen kommen fertig sortiert, Kennzahlen als eigener Endpunkt (`GET /api/vertraege/uebersicht`).
- Braucht ein Formular Rückmeldung beim Tippen, gibt es einen Vorschau-Endpunkt
  (`POST …/vorschau`, **200**, ohne Speichern), den die SPA verzögert aufruft. Er nutzt dieselbe Berechnung
  und dieselbe Bereinigung der Eingaben wie das Speichern.
- „Heute“ bestimmt das Backend (`Clock`, Europe/Berlin), nicht der Browser.

## Folgen

- Eine Quelle der Wahrheit: Liste, Kennzahlen, Vorschau und gespeicherter Vertrag zeigen dasselbe; die
  Regeln sind einmal getestet und in `docs/api.md` beschrieben.
- Mehr Anfragen (Vorschau beim Tippen); die SPA entprellt sie. Ohne Backend gibt es keine Vorschau.
- Prüfungen in der SPA dienen nur der Bedienung (z.&nbsp;B. Pflichtfelder markieren); maßgeblich sind die
  Feldfehler des Backends.
- Designs und Prototypen dürfen zur Anschauung rechnen, sind aber nicht maßgeblich; Abweichungen werden
  im Design behoben.
