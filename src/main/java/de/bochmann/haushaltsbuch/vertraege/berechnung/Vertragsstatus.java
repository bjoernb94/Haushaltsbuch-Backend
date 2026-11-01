package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Status eines Vertrags; wird nicht gespeichert, sondern berechnet ({@link Vertragsberechnung#status}). */
public enum Vertragsstatus {

	AKTIV,

	/** Gekündigt, läuft aber noch bis zum Kündigungsdatum. */
	GEKUENDIGT,

	BEENDET

}
