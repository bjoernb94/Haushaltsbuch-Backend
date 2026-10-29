package de.bochmann.haushaltsbuch.nutzer;

/**
 * Der Identity Provider hat die Registrierungsdaten abgelehnt, z.&nbsp;B. weil das Passwort
 * nicht der Passwort-Richtlinie des Realms entspricht.
 */
class RegistrierungAbgelehntException extends RuntimeException {

	RegistrierungAbgelehntException(String meldung) {
		super(meldung);
	}

}
