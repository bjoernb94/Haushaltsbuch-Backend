package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltAbfrage;
import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>
 * Angemeldet wird nur, wer im Backend einen Haushalt hat. Ein Konto, das nur in Keycloak existiert
 * (Abbruch bei der Registrierung, von Hand angelegt), wird abgewiesen und bei Keycloak gleich wieder
 * abgemeldet, statt halb angemeldet zu bleiben.
 */
public class Anmeldung {

	private static final Logger log = LoggerFactory.getLogger(Anmeldung.class);

	private final KeycloakTokenClient tokenClient;

	private final HaushaltAbfrage haushaltAbfrage;

	private final SessionAuthenticationStrategy sessionStrategie;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	private final SecurityContextHolderStrategy securityContextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();

	Anmeldung(KeycloakTokenClient tokenClient, HaushaltAbfrage haushaltAbfrage,
			SessionAuthenticationStrategy sessionStrategie) {
		this.tokenClient = tokenClient;
		this.haushaltAbfrage = haushaltAbfrage;
		this.sessionStrategie = sessionStrategie;
	}

	/**
	 * @throws AnmeldungFehlgeschlagenException wenn Keycloak die Anmeldedaten ablehnt oder das Konto im
	 *         Backend keinen Haushalt hat ({@link Grund#KONTO_UNVOLLSTAENDIG})
	 * @throws de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException wenn Keycloak nicht
	 *         rechtzeitig antwortet
	 */
	public void anmelden(String email, String passwort, HttpServletRequest request, HttpServletResponse response) {
		var anmeldedaten = tokenClient.anmelden(email, passwort);
		var nutzer = new DefaultOidcUser(List.of(), anmeldedaten.idToken(), IdTokenClaimNames.SUB);
		var authentication = new OAuth2AuthenticationToken(nutzer, nutzer.getAuthorities(), "keycloak");
		// Erst nach der Passwortprüfung: Wer das Passwort nicht kennt, erfährt nichts über das Konto.
		if (AngemeldeterNutzer.id(authentication).flatMap(haushaltAbfrage::haushaltVon).isEmpty()) {
			abweisen(nutzer.getSubject(), anmeldedaten.sitzung());
		}

		// Neue Session-ID und neues CSRF-Token, wie beim Login über Spring Security selbst.
		sessionStrategie.onAuthentication(authentication, request, response);
		var context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		request.getSession().setAttribute(KeycloakSitzung.ATTRIBUT, anmeldedaten.sitzung());
	}

	/** Beendet die eben begonnene Sitzung bei Keycloak; eine Session im Backend gibt es noch nicht. */
	private void abweisen(String kontoId, KeycloakSitzung sitzung) {
		// Keine E-Mail ins Log, die Keycloak-ID reicht dem Support (docs/betrieb.md, „Konten und Registrierung“).
		log.warn("Konto {} hat keinen Haushalt, Anmeldung abgewiesen.", kontoId);
		try {
			tokenClient.abmelden(sitzung.refreshToken());
		}
		catch (RuntimeException e) {
			log.warn("Keycloak-Sitzung von Konto {} konnte nicht beendet werden.", kontoId, e);
		}
		throw new AnmeldungFehlgeschlagenException(Grund.KONTO_UNVOLLSTAENDIG,
				"Account " + kontoId + " has no household.");
	}

}
