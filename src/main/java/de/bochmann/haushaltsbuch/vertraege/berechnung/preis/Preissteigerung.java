package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Regel, nach der ein Vertrag regelmäßig teurer wird, z.&nbsp;B. jährlich um 2,5&nbsp;%. Daraus rechnet
 * {@link Preisprognose}.
 *
 * @param prozent nur bei {@link Steigerungsart#PROZENT}
 * @param betrag nur bei {@link Steigerungsart#BETRAG}
 * @param naechsteAm Anker im Rhythmus wie {@code beginn} für die Abbuchungen; darf in der Vergangenheit liegen
 */
public record Preissteigerung(
		Steigerungsart art,
		@Nullable BigDecimal prozent,
		@Nullable Betrag betrag,
		Erhoehungsrhythmus rhythmus,
		LocalDate naechsteAm) {

	/**
	 * Der Preis nach einer Erhöhung, kaufmännisch gerundet: Ein prognostizierter Preis entsteht wie ein abgebuchter,
	 * die nächste Erhöhung rechnet mit dem gerundeten weiter (ADR 0011).
	 */
	public Betrag erhoeht(Betrag preis) {
		return switch (art) {
			case PROZENT -> preis.mal(BigDecimal.ONE.add(prozent.movePointLeft(2)));
			case BETRAG -> preis.plus(betrag);
		};
	}

}
