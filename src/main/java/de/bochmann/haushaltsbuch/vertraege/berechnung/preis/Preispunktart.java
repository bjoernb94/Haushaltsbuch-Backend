package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

/** Woher ein {@link Preispunkt} des Preisverlaufs stammt. */
public enum Preispunktart {

	/** Ein bisheriger Preis der Preisliste, auch der aktuelle. */
	GEZAHLT,

	/**
	 * Eine Stufe der {@link Preissteigerung}, die seit dem letzten Speichern fällig geworden ist; das nächste Speichern
	 * legt sie als automatischen Preis an. Gespeicherte automatische Preise sind {@link #GEZAHLT}.
	 */
	ERRECHNET,

	/** Ein künftiger Eintrag der Preisliste. */
	ANGEKUENDIGT,

	/** Ein Preis der {@link Preisprognose}. */
	PROGNOSE

}
