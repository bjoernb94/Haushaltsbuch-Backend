package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Liest Angaben zum angemeldeten Nutzer aus dessen Token, unabhängig davon, ob er per Session
 * ({@link Anmeldung}, ID-Token) oder per Bearer-Token (Access Token) angemeldet ist.
 */
public final class AngemeldeterNutzer {

	private AngemeldeterNutzer() {
	}

	/**
	 * @return die Benutzer-ID aus Keycloak (Claim {@code sub}), leer bei anonymen Anfragen
	 */
	public static Optional<UUID> id(Authentication authentication) {
		if (!(claims(authentication).get("sub") instanceof String subject)) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(subject));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/**
	 * Die Claims des Tokens, z.&nbsp;B. {@code given_name}, {@code family_name}, {@code email}.
	 * Sie stammen aus der Anmeldung und ändern sich erst mit der nächsten.
	 *
	 * @return die Claims, leer bei anonymen Anfragen
	 */
	public static Map<String, Object> claims(Authentication authentication) {
		if (authentication == null) {
			return Map.of();
		}
		return switch (authentication.getPrincipal()) {
			case Jwt jwt -> jwt.getClaims();
			case OidcUser oidcUser -> oidcUser.getClaims();
			case null, default -> Map.of();
		};
	}

}
