package de.bochmann.haushaltsbuch.vertraege.kategorie;

/**
 * Schlüssel der Standardkategorien, die die SPA übersetzt. Muss zu den Zeilen in {@code vertrag_kategorie}
 * passen (Migration V4): Einen unbekannten Wert kann Hibernate nicht laden.
 */
public enum VertragKategorieSchluessel {

	STREAMING_ABOS,
	SOFTWARE_APPS,
	VERSICHERUNG,
	MOBILFUNK_INTERNET,
	MITGLIEDSCHAFTEN,
	GEBUEHREN_BEITRAEGE,
	SONSTIGES

}
