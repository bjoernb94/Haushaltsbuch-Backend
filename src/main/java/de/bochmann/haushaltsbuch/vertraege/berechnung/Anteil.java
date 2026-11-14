package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Der eigene Anteil an einem geteilten Vertrag in Prozent (1–100, eine Nachkommastelle), z.&nbsp;B. 50 mit dem
 * Partner; ohne Angabe 100&nbsp;%. Übersichten zählen nur diesen Anteil der Kosten, abgebucht wird weiter der volle
 * Betrag.
 * <p>
 * {@link #von} rechnet ungerundet (ADR 0011): Der Anteil wird auf das ungerundete Ergebnis angewendet, Summen über
 * mehrere Verträge addieren die ungerundeten Anteile und runden erst am Ende.
 */
public record Anteil(BigDecimal prozent) {

	private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

	public static final Anteil GANZ = new Anteil(HUNDERT);

	/** @param prozent wie gespeichert; ohne Angabe {@link #GANZ} */
	public static Anteil aus(@Nullable BigDecimal prozent) {
		return prozent == null ? GANZ : new Anteil(prozent);
	}

	/** Unter 100&nbsp;%: nur dann zählt der Vertrag weniger als den vollen Betrag. */
	public boolean geteilt() {
		return prozent.compareTo(HUNDERT) < 0;
	}

	/** Der Anteil an {@code betrag}, ungerundet; exakt, weil {@code prozent} höchstens eine Nachkommastelle hat. */
	public Betrag von(Betrag betrag) {
		return betrag.malUngerundet(prozent.movePointLeft(2));
	}

}
