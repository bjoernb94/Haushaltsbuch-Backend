package de.bochmann.haushaltsbuch.vertraege.berechnung.kosten;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Einmalige Kosten eines Vertrags, z.&nbsp;B. eine Anschlussgebühr. Sie zählen nicht zu den laufenden Kosten
 * ({@code kostenProMonat}, {@code kostenImJahr}). Die Summen sind exakt (Beträge mit zwei Nachkommastellen).
 */
public record Einmalkosten(LocalDate faelligAm, Betrag betrag) {

	/** Summe aller Einträge; ohne Einträge 0 in {@code waehrung}. */
	public static Betrag summe(List<Einmalkosten> eintraege, Currency waehrung) {
		return eintraege.stream()
				.map(Einmalkosten::betrag)
				.reduce(Betrag.nullIn(waehrung), Betrag::plus);
	}

	/** Summe der im Kalenderjahr {@code jahr} fälligen Einträge; ohne solche 0 in {@code waehrung}. */
	public static Betrag imJahr(List<Einmalkosten> eintraege, int jahr, Currency waehrung) {
		return summe(eintraege.stream().filter(eintrag -> eintrag.faelligAm().getYear() == jahr).toList(), waehrung);
	}

}
