package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Wie und bis wann sich ein Vertrag kündigen lässt; siehe {@link Vertragsberechnung#kuendigung}. */
public enum Kuendigungsart {

	/** Kündigung bis {@code kuendigenBis} zum {@code zum}. */
	KUENDIGEN_BIS,

	/** Nach der Mindestlaufzeit jeden Monat kündbar; Daten wie bei {@link #KUENDIGEN_BIS}, nie dringend. */
	MONATLICH_KUENDBAR,

	/** Endet ohne Verlängerung zum {@code zum}, keine Kündigung nötig. */
	ENDET,

	/** Kein Vertragsende bestimmbar, z.&nbsp;B. weil Laufzeitende oder Verlängerung fehlen. */
	KEINE_LAUFZEIT,

	/** Nicht kündbar, z.&nbsp;B. Rundfunkbeitrag. */
	NICHT_KUENDBAR,

	/** Bereits gekündigt zum {@code zum}. */
	GEKUENDIGT,

	BEENDET

}
