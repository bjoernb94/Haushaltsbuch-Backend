package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.math.BigDecimal;

/**
 * Kosten eines Vertrags pro Jahr und Monat. Pro Jahr ist exakt (die Monate jeder Zahlweise teilen 12
 * ohne Rest); pro Monat wird aus dem Jahr gerundet. Summen über mehrere Verträge (#40) addieren daher
 * die Jahreswerte und runden erst am Ende.
 */
public final class Kosten {

	private Kosten() {
	}

	public static Betrag proJahr(Betrag betrag, Zahlweise zahlweise) {
		return betrag.mal(BigDecimal.valueOf(12 / zahlweise.monate()));
	}

	public static Betrag proMonat(Betrag betrag, Zahlweise zahlweise) {
		return proMonat(proJahr(betrag, zahlweise));
	}

	/** Monatswert aus einem Jahreswert, z.&nbsp;B. einer Summe. */
	public static Betrag proMonat(Betrag proJahr) {
		return proJahr.geteilt(12);
	}

}
