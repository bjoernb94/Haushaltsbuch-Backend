# 0012 Konflikterkennung per `version`, auch beim Löschen

**Status:** angenommen, 2026-11-01, nachgetragen mit
[#122](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/122) (umgesetzt mit
[#38](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/38) und
[#39](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/39))

## Kontext

Fachdaten gehören dem Haushalt (ADR [0004](0004-fachdaten-gehoeren-dem-haushalt.md)), mehrere Mitglieder
können also dasselbe Objekt gleichzeitig bearbeiten. Ohne Prüfung gewinnt, wer zuletzt speichert, und die
Änderung des anderen geht still verloren. Dasselbe gilt fürs Löschen: Wer einen Vertrag löscht, den ein
anderes Mitglied gerade geändert hat, löscht etwas, das er so nie gesehen hat.

## Entscheidung

Jede Fach-Entity hat eine Version (`@Version` in `FachEntity`), die Antworten enthalten sie als `version`.

- `PUT` verlangt sie im Body (Pflichtfeld), `DELETE` als Parameter (`?version=3`, Pflicht).
- Ist sie veraltet, antwortet das Backend mit **409** `KONFLIKT` und ändert bzw. löscht nichts. Das gilt
  auch, wenn zwei Anfragen gleichzeitig speichern (Fehler beim Flush).
- Die Antwort auf das `PUT` enthält die neue `version`; ein `PUT` ohne tatsächliche Änderung lässt sie
  gleich.

## Folgen

- Lieber einen Konflikt melden, als still zu überschreiben: Die SPA lädt nach einem 409 neu und zeigt den
  aktuellen Stand, das Mitglied entscheidet selbst.
- Die SPA muss die `version` mitführen, auch für Löschen aus einer Liste; ein bloßes `DELETE` auf die ID
  gibt es nicht.
- Kein Zusammenführen von Änderungen: Auch Änderungen an verschiedenen Feldern stehen im Konflikt. Das ist
  einfacher und reicht für Haushalte mit wenigen Mitgliedern.
