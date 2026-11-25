package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Instant;

/**
 * Erneuert die Keycloak-Sitzung, sobald das Access Token abläuft. Lehnt Keycloak das ab
 * (Sitzung abgelaufen oder beendet, Konto deaktiviert), wird die Session im Backend beendet und
 * der Request läuft anonym weiter, erhält also 401.
 * <p>
 * Läuft vor dem Laden des SecurityContext, damit eine hier beendete Session gar nicht erst
 * als angemeldet gilt.
 */
class SitzungErneuernFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SitzungErneuernFilter.class);

	private final KeycloakTokenClient tokenClient;

	SitzungErneuernFilter(KeycloakTokenClient tokenClient) {
		this.tokenClient = tokenClient;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			// Parallele Requests derselben Session sollen nur einmal erneuern.
			synchronized (WebUtils.getSessionMutex(session)) {
				erneuernFallsNoetig(session);
			}
		}
		filterChain.doFilter(request, response);
	}

	private void erneuernFallsNoetig(HttpSession session) {
		if (!(session.getAttribute(KeycloakSitzung.ATTRIBUT) instanceof KeycloakSitzung sitzung)
				|| !sitzung.mussErneuertWerden(Instant.now())) {
			return;
		}
		try {
			session.setAttribute(KeycloakSitzung.ATTRIBUT, tokenClient.erneuern(sitzung.refreshToken()));
		}
		catch (AnmeldungFehlgeschlagenException e) {
			log.info("Keycloak-Sitzung besteht nicht mehr ({}), Nutzer wird abgemeldet.", e.getMessage());
			session.invalidate();
		}
		catch (KeycloakNichtErreichbarException | RestClientException e) {
			// Keycloak nicht erreichbar: nicht alle abmelden, beim nächsten Request erneut versuchen.
			log.warn("Keycloak-Sitzung konnte nicht erneuert werden.", e);
		}
	}

}
