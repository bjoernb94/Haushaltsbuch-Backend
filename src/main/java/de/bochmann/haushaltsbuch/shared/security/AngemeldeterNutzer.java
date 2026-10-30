package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Ermittelt die Benutzer-ID des angemeldeten Nutzers (Claim {@code sub}) als {@link UUID},
 * unabhängig davon, ob er per Session ({@link Anmeldung}) oder per Bearer-Token angemeldet ist.
 */
public final class AngemeldeterNutzer {

	private AngemeldeterNutzer() {
	}

	/**
	 * @return die Benutzer-ID aus Keycloak (Claim {@code sub}), leer bei anonymen Anfragen
	 */
	public static Optional<UUID> id(Authentication authentication) {
		if (authentication == null) {
			return Optional.empty();
		}
		String subject = switch (authentication.getPrincipal()) {
			case Jwt jwt -> jwt.getSubject();
			case OidcUser oidcUser -> oidcUser.getSubject();
			case null, default -> null;
		};
		if (subject == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(subject));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
