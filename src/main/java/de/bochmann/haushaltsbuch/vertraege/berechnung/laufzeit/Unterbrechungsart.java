package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

/** Warum ein Vertrag eine Zeit lang nicht lief; siehe {@link Unterbrechung}. */
public enum Unterbrechungsart {

	/**
	 * Gekündigt und später wieder aufgenommen: Rechtlich war der Vertrag beendet. Geplante Pausen, in denen er
	 * weiterläuft, kommen später als eigene Art dazu (ADR 0023).
	 */
	KUENDIGUNG

}
