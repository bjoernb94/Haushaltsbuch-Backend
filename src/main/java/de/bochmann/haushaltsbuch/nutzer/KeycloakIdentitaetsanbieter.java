package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legt Benutzerkonten über die Keycloak Admin REST API an. Der übergebene {@link RestClient}
 * zeigt auf {@code .../admin/realms/<realm>} und authentifiziert sich als Service Account
 * (siehe {@link KeycloakAdminConfig}).
 */
class KeycloakIdentitaetsanbieter implements Identitaetsanbieter {

	private final RestClient restClient;

	KeycloakIdentitaetsanbieter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public UUID benutzerAnlegen(String email, String passwort, String vorname, String name) {
		Map<String, Object> benutzer = Map.of(
				"username", email,
				"email", email,
				"firstName", vorname,
				"lastName", name,
				"enabled", true,
				"credentials", List.of(Map.of("type", "password", "value", passwort, "temporary", false)));

		return KeycloakNichtErreichbarException.beiAufruf(() -> restClient.post()
				.uri("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.body(benutzer)
				.exchange((request, response) -> {
					if (response.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
						throw new EmailBereitsVergebenException(email);
					}
					if (response.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
						throw new RegistrierungAbgelehntException(fehlermeldung(response.bodyTo(Map.class)));
					}
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new IllegalStateException(
								"Keycloak hat das Anlegen des Kontos mit Status %s beantwortet."
										.formatted(response.getStatusCode()));
					}
					return idAusLocation(response.getHeaders().getLocation());
				}));
	}

	@Override
	public void benutzerLoeschen(UUID id) {
		KeycloakNichtErreichbarException.beiAufruf(
				() -> restClient.delete().uri("/users/{id}", id).retrieve().toBodilessEntity());
	}

	/** Keycloak liefert die ID des neuen Kontos nur im Location-Header ({@code .../users/<id>}). */
	private static UUID idAusLocation(URI location) {
		if (location == null) {
			throw new IllegalStateException("Keycloak hat keinen Location-Header geliefert.");
		}
		String pfad = location.getPath();
		return UUID.fromString(pfad.substring(pfad.lastIndexOf('/') + 1));
	}

	/** Keycloak meldet Fehler je nach Ursache unter unterschiedlichen Schlüsseln. */
	private static String fehlermeldung(Map<?, ?> fehler) {
		if (fehler != null) {
			for (String schluessel : List.of("error_description", "errorMessage", "error")) {
				if (fehler.get(schluessel) instanceof String meldung && !meldung.isBlank()) {
					return meldung;
				}
			}
		}
		return "Registration was rejected by Keycloak.";
	}

}
