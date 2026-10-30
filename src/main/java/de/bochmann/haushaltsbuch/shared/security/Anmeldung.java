package de.bochmann.haushaltsbuch.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

/**
 * Meldet einen Nutzer mit E-Mail und Passwort an, ohne Weiterleitung zu Keycloak: Das Backend
 * holt die Tokens direkt bei Keycloak und legt die Session an. Die SPA erhält nur das
 * Session-Cookie.
 * <p>
 * Der angemeldete Nutzer ist wie beim OIDC-Login ein {@link DefaultOidcUser};
 * {@link org.springframework.security.core.Authentication#getName()} liefert den Claim {@code sub}.
 */
public class Anmeldung {

	private final KeycloakTokenClient tokenClient;

	private final SessionAuthenticationStrategy sessionStrategie;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	private final SecurityContextHolderStrategy securityContextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();

	Anmeldung(KeycloakTokenClient tokenClient, SessionAuthenticationStrategy sessionStrategie) {
		this.tokenClient = tokenClient;
		this.sessionStrategie = sessionStrategie;
	}

	/**
	 * @throws AnmeldungFehlgeschlagenException wenn Keycloak die Anmeldedaten ablehnt
	 */
	public void anmelden(String email, String passwort, HttpServletRequest request, HttpServletResponse response) {
		var anmeldedaten = tokenClient.anmelden(email, passwort);
		var nutzer = new DefaultOidcUser(List.of(), anmeldedaten.idToken(), IdTokenClaimNames.SUB);
		var authentication = new OAuth2AuthenticationToken(nutzer, nutzer.getAuthorities(), "keycloak");

		// Neue Session-ID und neues CSRF-Token, wie beim Login über Spring Security selbst.
		sessionStrategie.onAuthentication(authentication, request, response);
		var context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		request.getSession().setAttribute(KeycloakSitzung.ATTRIBUT, anmeldedaten.sitzung());
	}

}
