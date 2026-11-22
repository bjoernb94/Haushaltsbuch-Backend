# 0022 Automatische Preiserhöhungen als Preise speichern

**Status:** angenommen, 2026-11-22, mit [#159](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/159)
(Ausnahme zu ADR [0014](0014-abgeleitete-werte-nicht-speichern.md))

## Kontext

Ein Vertrag kann eine Preissteigerung haben, z.&nbsp;B. „jährlich +2&nbsp;%“. Bisher rechnete das Backend daraus nur
eine Prognose künftiger Preise; vergangene Termine übersprang es. Wer einen Vertrag nachträglich anlegt (Beginn 2021),
sah deshalb den Preis von 2021 als aktuellen, und auch die Kosten waren zu niedrig.

Die vergangenen Stufen nur beim Lesen zu rechnen (ADR 0014) reicht nicht: Die echte Erhöhung weicht oft ab, der
Nutzer will einzelne Stufen korrigieren oder löschen können. Und ändert er die Regel, z.&nbsp;B. auf 3&nbsp;%, sollen
die vergangenen Preise nicht rückwirkend neu entstehen.

## Entscheidung

Beim Anlegen und Ändern legt das Backend die bis heute fälligen Stufen der Preissteigerung als Einträge der
Preisliste an, gekennzeichnet mit `automatisch`. Danach sind sie Angaben des Nutzers wie jeder andere Preis: Er kann
sie ändern (dann entfällt das Kennzeichen) und löschen (die Lücke bleibt). Eine geänderte Regel wirkt nur auf
künftige Stufen.

Stufen, die zwischen zwei Speichervorgängen fällig werden, berechnet das Backend beim Lesen weiterhin nur
(`ERRECHNET`, ADR 0014); ein `GET` schreibt nicht, und ein nächtlicher Job würde offene Formulare mit einem
Versionskonflikt (ADR [0012](0012-konflikterkennung-per-version.md)) treffen. Das nächste Speichern legt sie an.

## Folgen

- Gespeicherte Preise sind keine reinen Eingaben mehr; die Herkunft steht in `vertrag_preis.automatisch`. Die
  Rechenregeln stehen in `docs/api.md` (Preissteigerung, fällige Stufen).
- Ob ein Eintrag automatisch bleibt, prüft das Backend gegen den gespeicherten Stand, nicht die SPA.
- Bestehende Verträge werden nicht migriert: Ihre Stufen sind bis zum nächsten Speichern errechnet.
- Ein gelöschter automatischer Preis heißt „an diesem Termin keine Erhöhung“. Damit er nicht beim nächsten Speichern
  wieder entsteht, merkt sich der Vertrag, bis wann die Termine betrachtet sind (`preissteigerung_betrachtet_bis`,
  [#162](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/162)); die SPA schickt den Wert an die Vorschau.
- Es gibt zwei Wege zum selben Wert (gespeichert oder errechnet); `Preisprognose#faellige` rechnet beide gleich.
