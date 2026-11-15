package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Status eines Vertrags; wird nicht gespeichert, sondern berechnet ({@link Vertragsberechnung#status}). */
public enum Vertragsstatus {

	/** Beginnt erst nach heute; auch, wenn er schon zu einem Tag ab dem Beginn gekündigt ist. */
	NICHT_BEGONNEN,

	AKTIV,

	/** Gekündigt, läuft aber noch bis zum Kündigungsdatum. */
	GEKUENDIGT,

	BEENDET,

	/** Gekündigt zu einem Tag vor dem Beginn: läuft nie, Einmalkosten können trotzdem anfallen. */
	STORNIERT

}
