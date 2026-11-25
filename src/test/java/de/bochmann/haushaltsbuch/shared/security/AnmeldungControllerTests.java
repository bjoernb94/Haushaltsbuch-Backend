package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;
import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
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

	/** Aus {@code spring.http.clients.*}; daraus baut Boot die Request-Factory der Keycloak-Clients. */
	@Autowired
	private HttpClientSettings httpClientSettings;

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
	void nichtErreichbaresKeycloakBeiAnmeldung() throws Exception {
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenThrow(new KeycloakNichtErreichbarException(new ResourceAccessException("Read timed out")));

		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("KEYCLOAK_NICHT_ERREICHBAR"));
	}

	/**
	 * Konto nur in Keycloak (#179): abgewiesen, bei Keycloak gleich wieder abgemeldet, keine Session im
	 * Backend. Das Log nennt die Keycloak-ID, nicht die E-Mail.
	 */
	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void kontoOhneHaushaltAbgewiesen(CapturedOutput ausgabe) throws Exception {
		UUID kontoId = UUID.randomUUID();
		when(tokenClient.anmelden("max@example.org", "Geheim123!")).thenReturn(anmeldedaten(kontoId, "rt-ohne-haushalt"));

		var ergebnis = mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("KONTO_UNVOLLSTAENDIG"))
				.andReturn();

		verify(tokenClient).abmelden("rt-ohne-haushalt");
		MockHttpSession session = (MockHttpSession) ergebnis.getRequest().getSession(false);
		if (session != null) {
			assertThat(session.getAttribute(KeycloakSitzung.ATTRIBUT)).isNull();
			mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
					.andExpect(status().isUnauthorized());
		}
		assertThat(ausgabe.getAll())
				.contains("Konto " + kontoId + " hat keinen Haushalt, Anmeldung abgewiesen.")
				.doesNotContain("max@example.org");
	}

	/** Die Sitzung bei Keycloak läuft dann von selbst ab; der Nutzer bleibt trotzdem abgewiesen. */
	@Test
	void kontoOhneHaushaltAuchOhneKeycloakAbmeldungAbgewiesen() throws Exception {
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenReturn(anmeldedaten(UUID.randomUUID(), "rt-ohne-haushalt"));
		doThrow(new KeycloakNichtErreichbarException(new ResourceAccessException("Read timed out")))
				.when(tokenClient).abmelden("rt-ohne-haushalt");

		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KONTO_UNVOLLSTAENDIG"));
	}

	@Test
	void timeoutsFuerKeycloakKonfiguriert() {
		assertThat(httpClientSettings.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
		assertThat(httpClientSettings.readTimeout()).isEqualTo(Duration.ofSeconds(5));
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
		when(tokenClient.erneuern("rt"))
				.thenThrow(new KeycloakNichtErreichbarException(new ResourceAccessException("Read timed out")));

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
		doThrow(new KeycloakNichtErreichbarException(new ResourceAccessException("Read timed out")))
				.when(tokenClient).abmelden("rt");

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
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenReturn(new KeycloakTokenClient.Anmeldedaten(idToken(nutzerId),
						new KeycloakSitzung("rt", accessTokenAblauf)));

		var ergebnis = mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isNoContent())
				.andReturn();
		return (MockHttpSession) ergebnis.getRequest().getSession(false);
	}

	private static KeycloakTokenClient.Anmeldedaten anmeldedaten(UUID kontoId, String refreshToken) {
		return new KeycloakTokenClient.Anmeldedaten(idToken(kontoId),
				new KeycloakSitzung(refreshToken, Instant.now().plusSeconds(300)));
	}

	private static OidcIdToken idToken(UUID kontoId) {
		return OidcIdToken.withTokenValue("id-token")
				.subject(kontoId.toString())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.build();
	}

}
