package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Monate;

import java.time.LocalDate;

/** Wie oft eine {@link Preissteigerung} den Preis erhöht. */
public enum Erhoehungsrhythmus {

	VIERTELJAEHRLICH(3),
	HALBJAEHRLICH(6),
	JAEHRLICH(12),
	ZWEIJAEHRLICH(24),
	DREIJAEHRLICH(36);

	private final int monate;

	Erhoehungsrhythmus(int monate) {
		this.monate = monate;
	}

	/** Abstand zwischen zwei Erhöhungen in Monaten. */
	public int monate() {
		return monate;
	}

	/**
	 * Der Termin einen Rhythmus nach {@code datum}, z.&nbsp;B. der Vorschlag für die nächste Erhöhung nach dem Beginn.
	 * Ein Monatsletzter bleibt einer (31.01. vierteljährlich → 30.04., 30.04. → 31.07.), wie bei den Terminen der
	 * {@link Preisprognose}.
	 */
	public LocalDate nach(LocalDate datum) {
		return Monate.plus(datum, monate);
	}

}
