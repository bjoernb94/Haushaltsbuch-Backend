package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Wie ein Eintrag der {@link Preisliste} zum {@link Vertragsberechnung#aktuellerPreis aktuellen Preis} steht. */
public enum Preiszeilenart {

	/** Vor dem aktuellen Eintrag. */
	FRUEHER,

	/** Der Eintrag des aktuellen Preises; genau einer. */
	AKTUELL,

	/** Nach dem aktuellen Eintrag, gilt noch innerhalb der Vertragszeit. */
	ANGEKUENDIGT,

	/** Gilt ab einem Tag nach dem Vertragsende, also nie. */
	NACH_ENDE

}
