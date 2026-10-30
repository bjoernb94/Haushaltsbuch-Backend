package de.bochmann.haushaltsbuch.shared.security;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Tokens der Keycloak-Sitzung eines angemeldeten Nutzers, gespeichert in der HTTP-Session.
 * Das Access Token selbst wird nicht gebraucht; sein Ablauf bestimmt nur, wann die Sitzung
 * bei Keycloak erneuert und damit geprüft wird.
 */
record KeycloakSitzung(String refreshToken, Instant accessTokenAblauf) implements Serializable {

	static final String ATTRIBUT = KeycloakSitzung.class.getName();

	/** Etwas vor dem Ablauf erneuern, damit das Token während eines Requests nicht abläuft. */
	private static final Duration VORLAUF = Duration.ofSeconds(30);

	boolean mussErneuertWerden(Instant jetzt) {
		return jetzt.plus(VORLAUF).isAfter(accessTokenAblauf);
	}

}
