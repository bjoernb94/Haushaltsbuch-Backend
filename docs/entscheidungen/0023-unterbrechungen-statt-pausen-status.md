# 0023 Unterbrechungen statt Pausen-Status

**Status:** angenommen, 2026-11-24, mit [#166](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/166)

## Kontext

Manche Verträge werden gekündigt und später wieder aufgenommen, z.&nbsp;B. ein Streaming-Abo, das über den Sommer
gekündigt war, oder der Rundfunkbeitrag, der während des Zusammenlebens über die gemeinsame Wohnung lief. Bisher
blieb nur, einen neuen Vertrag anzulegen. Dann ist die Geschichte zerrissen: Preise, Preissteigerung, Einmalkosten
und Notizen stehen doppelt, und die Kosten im Jahr verteilen sich auf zwei Verträge.

Ein Status „pausiert“ am Vertrag reicht nicht: Er sagt nur, wie es heute ist. Wann der Vertrag lief und wann nicht,
braucht die Berechnung aber für jede Abbuchung, auch rückwirkend.

## Entscheidung

- **Wiederaufnahme statt neuem Vertrag:** Ein gekündigter oder beendeter Vertrag lässt sich wieder aufnehmen
  (`POST /api/vertraege/{id}/wiederaufnahme`). Die Zeit zwischen Vertragsende und Wiederaufnahme wird als
  **Unterbrechung** gespeichert (`vertrag_unterbrechung`: `von`, `bis`, `art`). Nur diese Angabe ist gespeichert;
  Phasen, Status (`UNTERBROCHEN`) und Abbuchungen berechnet das Backend (ADR
  [0014](0014-abgeleitete-werte-nicht-speichern.md)). Ohne Lücke entsteht keine Unterbrechung, der Vertrag läuft
  nahtlos im alten Takt weiter.
- **Preise nach Kalender:** In einer Unterbrechung wird nichts abgebucht, danach beginnt der Takt neu. Preise und
  Preissteigerung gelten aber weiter nach Kalender, auch in der Unterbrechung. Wer nach einer Erhöhung wieder
  einsteigt, zahlt den neuen Preis; ein neuer Betrag bei der Wiederaufnahme wird ein Preis ab diesem Tag.
- **Entfernen statt Ändern:** Unterbrechungen lassen sich nicht bearbeiten, nur über das `PUT` des Vertrags
  entfernen (`unterbrechungIds` nennt die, die bleiben). Es gibt keinen eigenen Endpunkt; die `version` des Vertrags
  deckt sie mit ab (ADR [0012](0012-konflikterkennung-per-version.md)). Ein falsches Datum korrigiert man, indem man
  die Unterbrechung entfernt, die Kündigung setzt und erneut wieder aufnimmt.
- **Art für spätere Pausen:** `art` ist heute immer `KUENDIGUNG` (rechtlich war der Vertrag beendet). Geplante
  Pausen, in denen der Vertrag weiterläuft, können als eigene Art dazukommen, ohne das Modell zu ändern.

## Folgen

- Laufzeit, Verlängerung und Kündigungstermine zählen ab dem Beginn der letzten Phase (`aktuellerBeginn`); die SPA
  rechnet keine Daten, auch nicht den frühesten Tag der Wiederaufnahme (`berechnet.wiederaufnahme`, ADR
  [0010](0010-berechnungen-nur-im-backend.md)).
- Beim Ändern und in der Vorschau müssen Beginn, Kündigung und Laufzeitende zu den verbleibenden Unterbrechungen
  passen; die Vorschau bekommt die Unterbrechungen als Werte mit, weil das Formular sie entfernen kann.
- Ein unterbrochener Vertrag zählt wie ein beendeter nicht als laufend, hat keine nächste Preisänderung und keine
  Prognose; die Liste sortiert ihn zwischen gekündigten und beendeten.
- Die Regeln stehen in `docs/api.md`, Abschnitt „Wieder aufnehmen“.
