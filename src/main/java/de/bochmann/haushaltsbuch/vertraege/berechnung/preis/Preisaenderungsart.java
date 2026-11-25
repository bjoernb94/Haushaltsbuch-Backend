package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

/** Woher eine {@link Preisaenderung} stammt. */
public enum Preisaenderungsart {

	/** Ein Eintrag der Preisliste in der Zukunft. */
	ANGEKUENDIGT,

	/** Ein Preis der {@link Preisprognose}. */
	PROGNOSE

}
