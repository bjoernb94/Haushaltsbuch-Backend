package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.KeycloakTest;
import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token-Endpunkt ({@link KeycloakTokenClient}), Registrierung, Anmeldung und Abmeldung über HTTP und der
 * Systemstatus gegen einen echten Keycloak mit unserem Realm. Konten entstehen über
 * {@code POST /api/registrierung}, die öffentliche Schnittstelle.
 */
@KeycloakTest
class KeycloakAnbindungTests {

	private static final String PASSWORT = "Geheim123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private KeycloakTokenClient tokenClient;

	@Test
	void registrierenAnmeldenUndAbmelden() throws Exception {
		String email = neueEmail();
		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(registrierung(email)))
				.andExpect(status().isCreated())
				.andReturn().getRequest().getSession(false);

		// Nach der Registrierung direkt angemeldet, Profil aus dem echten Token.
		mockMvc.perform(get("/api/nutzer/ich").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.vorname").value("Max"))
				.andExpect(jsonPath("$.name").value("Muster"));
		String refreshToken = ((KeycloakSitzung) session.getAttribute(KeycloakSitzung.ATTRIBUT)).refreshToken();

		mockMvc.perform(post("/api/abmeldung").with(csrf()).session(session))
				.andExpect(status().isNoContent());
		// Die Abmeldung beendet auch die Sitzung bei Keycloak.
		assertThatThrownBy(() -> tokenClient.erneuern(refreshToken))
				.isInstanceOf(AnmeldungFehlgeschlagenException.class);

		MockHttpSession neueSession = (MockHttpSession) mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"%s\", \"passwort\": \"%s\"}".formatted(email, PASSWORT)))
				.andExpect(status().isNoContent())
				.andReturn().getRequest().getSession(false);
		mockMvc.perform(get("/api/nutzer/ich").session(neueSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void anmeldenErneuernUndAbmeldenAmTokenEndpunkt() throws Exception {
		String email = registrieren();

		KeycloakTokenClient.Anmeldedaten anmeldung = tokenClient.anmelden(email, PASSWORT);
		assertThat(anmeldung.idToken().getEmail()).isEqualTo(email);

		KeycloakSitzung erneuert = tokenClient.erneuern(anmeldung.sitzung().refreshToken());
		assertThat(erneuert.refreshToken()).isNotBlank();

		tokenClient.abmelden(erneuert.refreshToken());
		assertThatThrownBy(() -> tokenClient.erneuern(erneuert.refreshToken()))
				.isInstanceOfSatisfying(AnmeldungFehlgeschlagenException.class,
						e -> assertThat(e.getGrund()).isEqualTo(Grund.UNGUELTIG));
	}

	@Test
	void falschesPasswortAbgelehnt() throws Exception {
		String email = registrieren();

		assertThatThrownBy(() -> tokenClient.anmelden(email, "Falsch123!"))
				.isInstanceOfSatisfying(AnmeldungFehlgeschlagenException.class,
						e -> assertThat(e.getGrund()).isEqualTo(Grund.UNGUELTIG));
		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"%s\", \"passwort\": \"Falsch123!\"}".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("ANMELDUNG_UNGUELTIG"));
	}

	@Test
	void vergebeneEmailUndAbgelehntesPasswortUeberHttp() throws Exception {
		String email = registrieren();

		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(registrierung(email)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_BEREITS_VERGEBEN"));
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(registrierung(neueEmail(), "geheim123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REGISTRIERUNG_ABGELEHNT"));
	}

	@Test
	void systemstatusMeldetKeycloakUp() throws Exception {
		mockMvc.perform(get("/actuator/health/systemstatus"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.keycloak.status").value("UP"));
	}

	private String registrieren() throws Exception {
		String email = neueEmail();
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(registrierung(email)))
				.andExpect(status().isCreated());
		return email;
	}

	private static String registrierung(String email) {
		return registrierung(email, PASSWORT);
	}

	private static String registrierung(String email, String passwort) {
		return """
				{"email": "%s", "passwort": "%s", "vorname": "Max", "name": "Muster", "komponenten": ["FINANZUEBERSICHT"]}
				""".formatted(email, passwort);
	}

	private static String neueEmail() {
		return "anbindung-" + UUID.randomUUID() + "@example.org";
	}

}
