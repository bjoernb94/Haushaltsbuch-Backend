package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Woher eine {@link Preisaenderung} stammt. */
public enum Preisaenderungsart {

	/** Ein Eintrag der Preisliste in der Zukunft. */
	ANGEKUENDIGT,

	/** Ein Preis der {@link Preisprognose}. */
	PROGNOSE

}
