package de.bochmann.haushaltsbuch.nutzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakIdentitaetsanbieterTests {

	private static final String ADMIN_URI = "http://keycloak.test/admin/realms/haushaltsbuch";

	private MockRestServiceServer server;

	private KeycloakIdentitaetsanbieter identitaetsanbieter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(ADMIN_URI);
		server = MockRestServiceServer.bindTo(builder).build();
		identitaetsanbieter = new KeycloakIdentitaetsanbieter(builder.build());
	}

	@Test
	void legtKontoAnUndLiefertIdAusLocation() {
		UUID id = UUID.randomUUID();
		server.expect(requestTo(ADMIN_URI + "/users"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.username").value("max@example.org"))
				.andExpect(jsonPath("$.email").value("max@example.org"))
				.andExpect(jsonPath("$.firstName").value("Max"))
				.andExpect(jsonPath("$.lastName").value("Muster"))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.credentials[0].type").value("password"))
				.andExpect(jsonPath("$.credentials[0].value").value("geheim123"))
				.andExpect(jsonPath("$.credentials[0].temporary").value(false))
				.andRespond(withCreatedEntity(URI.create(ADMIN_URI + "/users/" + id)));

		assertThat(identitaetsanbieter.benutzerAnlegen("max@example.org", "geheim123", "Max", "Muster"))
				.isEqualTo(id);
		server.verify();
	}

	@Test
	void vorhandenesKontoAlsKonflikt() {
		server.expect(requestTo(ADMIN_URI + "/users"))
				.andRespond(withStatus(CONFLICT).contentType(MediaType.APPLICATION_JSON)
						.body("{\"errorMessage\": \"User exists with same username\"}"));

		assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen("max@example.org", "geheim123", "Max", "Muster"))
				.isInstanceOf(EmailBereitsVergebenException.class);
	}

	@Test
	void passwortRichtlinieAlsAblehnung() {
		server.expect(requestTo(ADMIN_URI + "/users"))
				.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"invalidPasswordMinLengthMessage\","
								+ " \"error_description\": \"Invalid password: minimum length 12.\"}"));

		assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen("max@example.org", "kurz", "Max", "Muster"))
				.isInstanceOf(RegistrierungAbgelehntException.class)
				.hasMessage("Invalid password: minimum length 12.");
	}

	/** Z.&nbsp;B. fehlende Rolle oder fehlender Scope (#113): ein Konfigurationsfehler, kein Fehler des Nutzers. */
	@Test
	void andererStatusIstFehler() {
		server.expect(requestTo(ADMIN_URI + "/users")).andRespond(withStatus(FORBIDDEN));

		assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen("max@example.org", "geheim123", "Max", "Muster"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("403");
	}

	@Test
	void ohneLocationIstFehler() {
		server.expect(requestTo(ADMIN_URI + "/users")).andRespond(withSuccess());

		assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen("max@example.org", "geheim123", "Max", "Muster"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Location");
	}

	/** Ohne Text vom Identity Provider eine allgemeine Meldung; ein leerer Text zählt wie keiner. */
	@Test
	void ablehnungOhneText() {
		server.expect(requestTo(ADMIN_URI + "/users")).andRespond(withBadRequest());
		server.expect(requestTo(ADMIN_URI + "/users"))
				.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{\"errorMessage\": \" \"}"));

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> identitaetsanbieter.benutzerAnlegen("max@example.org", "kurz", "Max", "Muster"))
					.isInstanceOf(RegistrierungAbgelehntException.class)
					.hasMessage("Registration was rejected by Keycloak.");
		}
	}

	@Test
	void loeschtKonto() {
		UUID id = UUID.randomUUID();
		server.expect(requestTo(ADMIN_URI + "/users/" + id))
				.andExpect(method(HttpMethod.DELETE))
				.andRespond(withNoContent());

		identitaetsanbieter.benutzerLoeschen(id);
		server.verify();
	}

}
