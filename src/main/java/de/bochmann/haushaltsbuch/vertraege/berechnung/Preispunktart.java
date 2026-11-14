package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Woher ein {@link Preispunkt} des Preisverlaufs stammt. */
public enum Preispunktart {

	/** Ein bisheriger Preis der Preisliste, auch der heute gültige. */
	GEZAHLT,

	/** Ein künftiger Eintrag der Preisliste. */
	ANGEKUENDIGT,

	/** Ein Preis der {@link Preisprognose}. */
	PROGNOSE

}
