package de.bochmann.haushaltsbuch.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

/**
 * Beendet beim Logout auch die Sitzung bei Keycloak. Läuft vor dem Beenden der Session im
 * Backend, solange das Refresh Token noch in der Session liegt.
 */
class KeycloakLogoutHandler implements LogoutHandler {

	private static final Logger log = LoggerFactory.getLogger(KeycloakLogoutHandler.class);

	private final KeycloakTokenClient tokenClient;

	KeycloakLogoutHandler(KeycloakTokenClient tokenClient) {
		this.tokenClient = tokenClient;
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(KeycloakSitzung.ATTRIBUT) instanceof KeycloakSitzung sitzung) {
			try {
				tokenClient.abmelden(sitzung.refreshToken());
			}
			catch (RuntimeException e) {
				// Die Session im Backend wird trotzdem beendet; die bei Keycloak läuft dann von selbst ab.
				log.warn("Keycloak-Sitzung konnte nicht beendet werden.", e);
			}
		}
	}

}
