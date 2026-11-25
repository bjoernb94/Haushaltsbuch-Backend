package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

/** Status eines Vertrags; wird nicht gespeichert, sondern berechnet ({@link Laufzeitberechnung#status}). */
public enum Vertragsstatus {

	/** Beginnt erst nach heute; auch, wenn er schon zu einem Tag ab dem Beginn gekündigt ist. */
	NICHT_BEGONNEN,

	AKTIV,

	/** Gekündigt, läuft aber noch bis zum Kündigungsdatum. */
	GEKUENDIGT,

	/**
	 * Heute in einer {@link Unterbrechung}: gekündigt und zu einem späteren Tag wieder aufgenommen. Zählt wie ein
	 * beendeter Vertrag nicht als laufend.
	 */
	UNTERBROCHEN,

	BEENDET,

	/** Gekündigt zu einem Tag vor dem Beginn: läuft nie, Einmalkosten können trotzdem anfallen. */
	STORNIERT

}
