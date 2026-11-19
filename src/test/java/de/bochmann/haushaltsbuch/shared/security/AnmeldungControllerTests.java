package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AnmeldungControllerTests {

	private static final String ANFRAGE = """
			{"email": "max@example.org", "passwort": "Geheim123!"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private KeycloakTokenClient tokenClient;

	@Autowired
	private HaushaltService haushaltService;

	@Test
	void anmeldungLegtSessionAn() throws Exception {
		MockHttpSession session = anmelden(Instant.now().plusSeconds(300));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isOk());
	}

	@Test
	void falschesPasswortAbgelehnt() throws Exception {
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenThrow(new AnmeldungFehlgeschlagenException(Grund.UNGUELTIG, "Invalid user credentials"));

		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ANMELDUNG_UNGUELTIG"))
				.andExpect(jsonPath("$.detail").value("Invalid email or password."));
	}

	@Test
	void gesperrtesKontoAbgelehnt() throws Exception {
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenThrow(new AnmeldungFehlgeschlagenException(Grund.GESPERRT, "Account disabled"));

		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ANMELDUNG_GESPERRT"));
	}

	@Test
	void anmeldungOhneFelderUngueltig() throws Exception {
		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[*].feld").value(containsInAnyOrder("email", "passwort")))
				.andExpect(jsonPath("$.felder[*].code").value(containsInAnyOrder("NotBlank", "NotBlank")));
	}

	@Test
	void anmeldungOhneCsrfTokenAbgelehnt() throws Exception {
		mockMvc.perform(post("/api/anmeldung")
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isForbidden());
	}

	@Test
	void abmeldungBeendetAuchKeycloakSitzung() throws Exception {
		MockHttpSession session = anmelden(Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/abmeldung").with(csrf()).session(session))
				.andExpect(status().isNoContent());

		verify(tokenClient).abmelden("rt");
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void abgelaufenesTokenWirdErneuert() throws Exception {
		MockHttpSession session = anmelden(Instant.now().minusSeconds(1));
		when(tokenClient.erneuern("rt")).thenReturn(new KeycloakSitzung("rt-neu", Instant.now().plusSeconds(300)));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isOk());

		verify(tokenClient).erneuern("rt");
		assertThat(session.getAttribute(KeycloakSitzung.ATTRIBUT))
				.isInstanceOfSatisfying(KeycloakSitzung.class, s -> assertThat(s.refreshToken()).isEqualTo("rt-neu"));
	}

	/** Keycloak nicht erreichbar: nicht abmelden, beim nächsten Request erneut versuchen. */
	@Test
	void nichtErreichbaresKeycloakBeimErneuernMeldetNichtAb() throws Exception {
		MockHttpSession session = anmelden(Instant.now().minusSeconds(1));
		when(tokenClient.erneuern("rt")).thenThrow(new ResourceAccessException("Connection refused"));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isOk());

		assertThat(session.isInvalid()).isFalse();
		assertThat(session.getAttribute(KeycloakSitzung.ATTRIBUT))
				.isInstanceOfSatisfying(KeycloakSitzung.class, s -> assertThat(s.refreshToken()).isEqualTo("rt"));
	}

	/** Die Session im Backend endet trotzdem; die bei Keycloak läuft von selbst ab. */
	@Test
	void abmeldungGelingtAuchOhneKeycloak() throws Exception {
		MockHttpSession session = anmelden(Instant.now().plusSeconds(300));
		doThrow(new ResourceAccessException("Connection refused")).when(tokenClient).abmelden("rt");

		mockMvc.perform(post("/api/abmeldung").with(csrf()).session(session))
				.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void beendeteKeycloakSitzungMeldetAb() throws Exception {
		MockHttpSession session = anmelden(Instant.now().minusSeconds(1));
		when(tokenClient.erneuern("rt"))
				.thenThrow(new AnmeldungFehlgeschlagenException(Grund.UNGUELTIG, "Session not active"));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NICHT_ANGEMELDET"));

		assertThat(session.isInvalid()).isTrue();
	}

	/** Meldet einen Nutzer mit Haushalt an, sonst gäbe {@code /api/nutzer/ich/komponenten} 404. */
	private MockHttpSession anmelden(Instant accessTokenAblauf) throws Exception {
		UUID nutzerId = UUID.randomUUID();
		haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));
		var idToken = OidcIdToken.withTokenValue("id-token")
				.subject(nutzerId.toString())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.build();
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenReturn(new KeycloakTokenClient.Anmeldedaten(idToken, new KeycloakSitzung("rt", accessTokenAblauf)));

		var ergebnis = mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isNoContent())
				.andReturn();
		return (MockHttpSession) ergebnis.getRequest().getSession(false);
	}

}
