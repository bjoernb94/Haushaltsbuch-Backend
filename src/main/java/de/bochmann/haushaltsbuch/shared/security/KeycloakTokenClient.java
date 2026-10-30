package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Spricht mit den OpenID-Connect-Endpunkten des Realms: Anmeldung per Passwort ("Direct access
 * grants"), Erneuerung und Beenden der Sitzung. Der übergebene {@link RestClient} zeigt auf
 * {@code <issuer>/protocol/openid-connect}.
 */
class KeycloakTokenClient {

	private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	private final JwtDecoder jwtDecoder;

	private final String clientId;

	private final String clientSecret;

	KeycloakTokenClient(RestClient restClient, JwtDecoder jwtDecoder, String clientId, String clientSecret) {
		this.restClient = restClient;
		this.jwtDecoder = jwtDecoder;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	record Anmeldedaten(OidcIdToken idToken, KeycloakSitzung sitzung) {
	}

	/**
	 * @throws AnmeldungFehlgeschlagenException wenn Keycloak die Anmeldedaten ablehnt
	 */
	Anmeldedaten anmelden(String benutzername, String passwort) {
		var formular = formular("password");
		formular.add("username", benutzername);
		formular.add("password", passwort);
		formular.add("scope", "openid");
		Map<String, Object> antwort = tokenAnfrage(formular);

		// Das ID-Token kommt direkt vom Token-Endpunkt, wird aber trotzdem vollständig geprüft.
		Jwt idToken = jwtDecoder.decode((String) antwort.get("id_token"));
		return new Anmeldedaten(new OidcIdToken(idToken.getTokenValue(), idToken.getIssuedAt(),
				idToken.getExpiresAt(), idToken.getClaims()), sitzung(antwort));
	}

	/**
	 * @throws AnmeldungFehlgeschlagenException wenn die Sitzung bei Keycloak nicht mehr besteht,
	 *         z.&nbsp;B. abgelaufen, beendet oder Konto deaktiviert
	 */
	KeycloakSitzung erneuern(String refreshToken) {
		var formular = formular("refresh_token");
		formular.add("refresh_token", refreshToken);
		return sitzung(tokenAnfrage(formular));
	}

	/** Beendet die Sitzung bei Keycloak, ohne Weiterleitung über den Browser. */
	void abmelden(String refreshToken) {
		var formular = formular(null);
		formular.add("refresh_token", refreshToken);
		restClient.post()
				.uri("/logout")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(formular)
				.retrieve()
				.toBodilessEntity();
	}

	private MultiValueMap<String, String> formular(String grantType) {
		var formular = new LinkedMultiValueMap<String, String>();
		if (grantType != null) {
			formular.add("grant_type", grantType);
		}
		formular.add("client_id", clientId);
		formular.add("client_secret", clientSecret);
		return formular;
	}

	private Map<String, Object> tokenAnfrage(MultiValueMap<String, String> formular) {
		return restClient.post()
				.uri("/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(formular)
				.exchange((request, response) -> {
					Map<String, Object> antwort = response.bodyTo(JSON);
					if (response.getStatusCode().is2xxSuccessful() && antwort != null) {
						return antwort;
					}
					String fehler = antwort != null && antwort.get("error") instanceof String s ? s : null;
					String beschreibung = antwort != null && antwort.get("error_description") instanceof String s
							? s : null;
					// invalid_grant: Anmeldedaten bzw. Refresh Token abgelehnt. Alles andere
					// (z. B. unauthorized_client, invalid_client) ist ein Konfigurationsfehler.
					if ("invalid_grant".equals(fehler)) {
						throw new AnmeldungFehlgeschlagenException(grund(beschreibung), beschreibung);
					}
					throw new IllegalStateException("Keycloak hat die Token-Anfrage mit Status %s abgelehnt: %s (%s)"
							.formatted(response.getStatusCode(), fehler, beschreibung));
				});
	}

	/** Keycloak unterscheidet die Gründe nur über den Text von {@code error_description}. */
	private static Grund grund(String beschreibung) {
		return switch (beschreibung) {
			case "Account disabled", "Account temporarily disabled" -> Grund.GESPERRT;
			case "Account is not fully set up" -> Grund.NICHT_EINGERICHTET;
			case null, default -> Grund.UNGUELTIG;
		};
	}

	private static KeycloakSitzung sitzung(Map<String, Object> antwort) {
		long gueltigSekunden = ((Number) antwort.get("expires_in")).longValue();
		return new KeycloakSitzung((String) antwort.get("refresh_token"), Instant.now().plusSeconds(gueltigSekunden));
	}

}
