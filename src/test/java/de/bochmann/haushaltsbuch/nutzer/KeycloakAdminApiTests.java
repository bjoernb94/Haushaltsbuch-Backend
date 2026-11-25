package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.KeycloakTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin API ({@link KeycloakIdentitaetsanbieter}) gegen einen echten Keycloak mit unserem Realm. */
@KeycloakTest
class KeycloakAdminApiTests {

	private static final String PASSWORT = "Geheim123!";

	@Autowired
	private Identitaetsanbieter identitaetsanbieter;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private MockMvc mockMvc;

	/** Ohne den Scope antwortet die Admin API ab Keycloak 26.8.2 mit 401 (#113). */
	@Test
	void fordertDenScopeDerAdminApiAn() {
		assertThat(clientRegistrations.findByRegistrationId(KeycloakAdminConfig.CLIENT_REGISTRATION_ID).getScopes())
				.containsExactly("kc-admin-api");
	}

	@Test
	void legtKontoAnUndLoeschtEs() {
		UUID id = identitaetsanbieter.benutzerAnlegen(neueEmail(), PASSWORT, "Max", "Muster");

		identitaetsanbieter.benutzerLoeschen(id);

		assertThatThrownBy(() -> identitaetsanbieter.benutzerLoeschen(id))
				.isInstanceOf(HttpClientErrorException.NotFound.class);
	}

	/** Konto nur in Keycloak, ohne Nutzer und Haushalt im Backend (#179). */
	@Test
	void anmeldungMitKontoOhneHaushaltAbgewiesen() throws Exception {
		String email = neueEmail();
		UUID id = identitaetsanbieter.benutzerAnlegen(email, PASSWORT, "Max", "Muster");
		try {
			mockMvc.perform(post("/api/anmeldung").with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\": \"%s\", \"passwort\": \"%s\"}".formatted(email, PASSWORT)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("KONTO_UNVOLLSTAENDIG"));
		}
		finally {
			identitaetsanbieter.benutzerLoeschen(id);
		}
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
