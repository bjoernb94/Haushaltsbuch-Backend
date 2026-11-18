package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.KeycloakTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admin API ({@link KeycloakIdentitaetsanbieter}) gegen einen echten Keycloak mit unserem Realm. */
@KeycloakTest
class KeycloakAdminApiTests {

	private static final String PASSWORT = "Geheim123!";

	@Autowired
	private Identitaetsanbieter identitaetsanbieter;

	@Test
	void legtKontoAnUndLoeschtEs() {
		UUID id = identitaetsanbieter.benutzerAnlegen(neueEmail(), PASSWORT, "Max", "Muster");

		identitaetsanbieter.benutzerLoeschen(id);

		assertThatThrownBy(() -> identitaetsanbieter.benutzerLoeschen(id))
				.isInstanceOf(HttpClientErrorException.NotFound.class);
	}

	@Test
	void vergebeneEmailAbgelehnt() {
		String email = neueEmail();
		UUID id = identitaetsanbieter.benutzerAnlegen(email, PASSWORT, "Max", "Muster");
		try {
			assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen(email, PASSWORT, "Erika", "Muster"))
					.isInstanceOf(EmailBereitsVergebenException.class);
		}
		finally {
			identitaetsanbieter.benutzerLoeschen(id);
		}
	}

	@Test
	void passwortGegenRichtlinieAbgelehnt() {
		// Lang genug für unsere Validierung, aber ohne Großbuchstaben und Sonderzeichen (Richtlinie im Realm).
		assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen(neueEmail(), "geheim123", "Max", "Muster"))
				.isInstanceOf(RegistrierungAbgelehntException.class);
	}

	private static String neueEmail() {
		return "admin-api-" + UUID.randomUUID() + "@example.org";
	}

}
