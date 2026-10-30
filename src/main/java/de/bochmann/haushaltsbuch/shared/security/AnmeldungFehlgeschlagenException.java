package de.bochmann.haushaltsbuch.shared.security;

/**
 * Keycloak hat die Anmeldedaten oder die Erneuerung der Sitzung abgelehnt.
 */
public class AnmeldungFehlgeschlagenException extends RuntimeException {

	public enum Grund {

		/** Unbekannte E-Mail-Adresse oder falsches Passwort; bei der Erneuerung: Sitzung beendet. */
		UNGUELTIG,

		/** Konto deaktiviert oder nach zu vielen Fehlversuchen vorübergehend gesperrt. */
		GESPERRT,

		/** Für das Konto stehen in Keycloak noch Pflichtschritte aus ("Required actions"). */
		NICHT_EINGERICHTET

	}

	private final Grund grund;

	AnmeldungFehlgeschlagenException(Grund grund, String meldung) {
		super(meldung);
		this.grund = grund;
	}

	public Grund getGrund() {
		return grund;
	}

}
