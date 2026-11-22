package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Woher ein {@link Preis} stammt. */
public enum Preisherkunft {

	/** Vom Nutzer eingetragen, auch eine geänderte automatische Stufe. */
	EINGETRAGEN,

	/** Eine fällige Stufe der {@link Preissteigerung}, beim Speichern angelegt (ADR 0022). */
	AUTOMATISCH,

	/** Eine seit dem letzten Speichern fällig gewordene Stufe, nur beim Lesen berechnet. */
	ERRECHNET,

	/** Ein künftiger Preis der {@link Preisprognose}. */
	PROGNOSE

}
