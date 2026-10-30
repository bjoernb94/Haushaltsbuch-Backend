package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "keycloak"})
class AnmeldungControllerTests {

	private static final String ANFRAGE = """
			{"email": "max@example.org", "passwort": "Geheim123!"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private KeycloakTokenClient tokenClient;

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
				.andExpect(jsonPath("$.grund").value("UNGUELTIG"))
				.andExpect(jsonPath("$.detail").value("E-Mail-Adresse oder Passwort ist falsch."));
	}

	@Test
	void gesperrtesKontoAbgelehnt() throws Exception {
		when(tokenClient.anmelden("max@example.org", "Geheim123!"))
				.thenThrow(new AnmeldungFehlgeschlagenException(Grund.GESPERRT, "Account disabled"));

		mockMvc.perform(post("/api/anmeldung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(ANFRAGE))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.grund").value("GESPERRT"));
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

	@Test
	void beendeteKeycloakSitzungMeldetAb() throws Exception {
		MockHttpSession session = anmelden(Instant.now().minusSeconds(1));
		when(tokenClient.erneuern("rt"))
				.thenThrow(new AnmeldungFehlgeschlagenException(Grund.UNGUELTIG, "Session not active"));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isUnauthorized());

		assertThat(session.isInvalid()).isTrue();
	}

	private MockHttpSession anmelden(Instant accessTokenAblauf) throws Exception {
		var idToken = OidcIdToken.withTokenValue("id-token")
				.subject(UUID.randomUUID().toString())
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
