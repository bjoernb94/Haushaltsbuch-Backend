# 0011 Rundung von Geldbeträgen

**Status:** angenommen, 2026-11-14 ([#92](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/92))

## Kontext

Geldbeträge werden umgerechnet (pro Monat, pro Jahr), summiert, mit Anteilen multipliziert und in die
Zukunft fortgeschrieben (Prognose). Rundet man dabei Zwischenergebnisse, summieren sich die Fehler und
das Ergebnis hängt vom Rechenweg ab: 10&nbsp;€ vierteljährlich sind pro Monat 3,33&nbsp;€, mal 3
Verträge 9,99&nbsp;€, ungerundet gerechnet aber 10,00&nbsp;€. Bisher stand die Regel nur im Code
(`Betrag.RUNDUNG`, `Kosten`) und knapp in den Docs. Sie gilt für alle Komponenten, nicht nur für
Verträge.

## Entscheidung

Gerechnet wird mit ungerundeten Werten. Gerundet wird nur, wo ein Betrag **entsteht** (z.&nbsp;B. ein
Preis oder eine Abbuchung) oder wo ein Ergebnis **ausgegeben** wird; Zwischenergebnisse nie.

- **Wie:** kaufmännisch (`HALF_UP`, 2,345 → 2,35; negative Beträge von der Null weg, −2,345 → −2,35) auf
  die Nachkommastellen der Währung. Heute sind das fest 2 (`NUMERIC(19, 2)`, `@Digits(fraction = 2)`);
  Nachkommastellen je Währung kommen mit den Fremdwährungen
  ([#30](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/30)).
- **Eingaben** mit mehr Nachkommastellen werden abgelehnt, nicht gerundet.
- **Exakt rechnen:** Wo möglich wird zuletzt geteilt (Preis × Anteil × 12 ÷ Monate statt
  Preis ÷ Monate × Anteil). Geht eine Division nicht auf, wird mit hoher Genauigkeit
  (`MathContext.DECIMAL128`) weitergerechnet und erst bei der Ausgabe gerundet.

Beispiele aus den Verträgen:

- **Pro Monat:** Betrag ÷ Monate der Zahlweise, gerundet bei der Ausgabe (10&nbsp;€ vierteljährlich →
  3,333… → 3,33&nbsp;€).
- **Summen:** ungerundete Werte addieren, erst die Summe runden. Die Monatssumme über mehrere Verträge
  ist die Summe der auf ein Jahr umgerechneten Preise ÷ 12.
- **Anteil:** Er wird auf das ungerundete Ergebnis angewendet, nicht auf gerundete Einzelwerte: innerhalb
  eines Vertrags auf die Summe seiner Abbuchungen, über mehrere Verträge je Vertrag ungerundet, dann
  summiert und gerundet. Der eigene Anteil pro Monat kommt aus dem exakten Monatswert, nicht aus dem
  gerundeten.
- **Prognose:** Ein prognostizierter Preis ist ein Betrag, der so abgebucht würde, und entsteht damit; er
  wird gerundet, und die nächste Erhöhung rechnet mit dem gerundeten Preis weiter (wie ein Anbieter, der
  jeden neuen Preis rundet). 100&nbsp;€ + 2,5&nbsp;% = 102,50&nbsp;€, + 2,5&nbsp;% = 105,06&nbsp;€
  (ungerundet 105,0625).

## Folgen

- Verbindlich ist nur der Betrag je Abbuchung. Umgerechnete Werte (pro Monat, Anteil, Summen, Prognose)
  sind gerundet und können um wenige Cent abweichen: Monatswert × 12 ist nicht der Jahreswert
  (Preisänderungen im Jahr, Rundung), die Summe der einzeln angezeigten Werte nicht die angezeigte Summe
  (3 × 3,33&nbsp;€ = 9,99&nbsp;€, Summe 10,00&nbsp;€). Das ist gewollt; die SPA weist allgemein darauf
  hin ([Frontend #89](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/89)) und gleicht nichts
  aus (ADR [0010](0010-berechnungen-nur-im-backend.md)).
- Das Ergebnis hängt nicht vom Rechenweg ab, solange nur am Ende gerundet wird; Tests prüfen gerundete
  Ausgaben, auch für Fälle, in denen Rundung pro Schritt anders ausginge.
- `Betrag.mal` und `Betrag.geteilt` runden sofort und eignen sich nur für den letzten Schritt.
  Zwischenergebnisse brauchen einen ungerundeten Rechenweg (`BigDecimal` oder eine Methode ohne Rundung).
