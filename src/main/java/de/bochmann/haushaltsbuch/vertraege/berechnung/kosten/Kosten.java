package de.bochmann.haushaltsbuch.vertraege.berechnung.kosten;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;

import java.math.BigDecimal;

/**
 * Ein Betrag auf Jahr und Monat umgerechnet (ADR 0011). Pro Jahr ist exakt (die Monate jeder Zahlweise teilen 12
 * ohne Rest); pro Monat wird aus dem Jahr gerundet. Summen über mehrere Verträge addieren daher die Jahreswerte
 * und runden erst am Ende. Umgerechnet, nicht abgebucht: Was im Kalenderjahr tatsächlich abgebucht wird, rechnet
 * {@link Kostenberechnung#kostenImJahr}.
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
