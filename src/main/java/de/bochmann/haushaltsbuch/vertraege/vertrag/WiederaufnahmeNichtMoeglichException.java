package de.bochmann.haushaltsbuch.vertraege.vertrag;

/**
 * Der Vertrag lässt sich nicht wieder aufnehmen, weil er weder gekündigt noch beendet ist oder kein Vertragsende hat
 * (z.&nbsp;B. storniert, aktiv oder unterbrochen): 409 {@code WIEDERAUFNAHME_NICHT_MOEGLICH}.
 */
class WiederaufnahmeNichtMoeglichException extends RuntimeException {

	WiederaufnahmeNichtMoeglichException(String detail) {
		super(detail);
	}

}
