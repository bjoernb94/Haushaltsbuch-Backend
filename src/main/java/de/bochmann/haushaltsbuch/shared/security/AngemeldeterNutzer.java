package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Liest Angaben zum angemeldeten Nutzer aus dem ID-Token seiner Anmeldung ({@link Anmeldung}).
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
		return authentication.getPrincipal() instanceof OidcUser oidcUser ? oidcUser.getClaims() : Map.of();
	}

}
