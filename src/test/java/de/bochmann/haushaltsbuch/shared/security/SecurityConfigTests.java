package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private KomponentenAbfrage komponentenAbfrage;

	/** Statt des echten Indikators, damit die Tests nicht von einem laufenden Keycloak abhängen. */
	@MockitoBean(name = "keycloakHealthIndicator")
	private HealthIndicator keycloakHealthIndicator;

	@BeforeEach
	void keycloakErreichbar() {
		when(keycloakHealthIndicator.health(anyBoolean())).thenReturn(Health.up().build());
	}

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void systemstatusIsPublicWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health/systemstatus"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.db.status").value("UP"))
				.andExpect(jsonPath("$.components.keycloak.status").value("UP"))
				.andExpect(jsonPath("$.components.db.details").doesNotExist())
				.andExpect(jsonPath("$.components.diskSpace").doesNotExist());
	}

	@Test
	void systemstatusIsDownWithoutKeycloak() throws Exception {
		when(keycloakHealthIndicator.health(anyBoolean())).thenReturn(Health.down().build());

		mockMvc.perform(get("/actuator/health/systemstatus"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.components.keycloak.status").value("DOWN"));
	}

	@Test
	void openApiIsPublicInDev() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/buchungen")).andExpect(status().isUnauthorized());
	}

	@Test
	void bereichMitGebuchterKomponenteErlaubt() throws Exception {
		UUID id = UUID.randomUUID();
		when(komponentenAbfrage.komponentenVon(id)).thenReturn(Set.of(Komponente.PORTFOLIO));

		// Noch kein Controller unter /api/portfolio: 404 heißt, die Security hat durchgelassen.
		mockMvc.perform(get("/api/portfolio").with(jwt().jwt(token -> token.subject(id.toString()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void bereichOhneGebuchteKomponenteVerboten() throws Exception {
		UUID id = UUID.randomUUID();
		when(komponentenAbfrage.komponentenVon(id)).thenReturn(Set.of(Komponente.PORTFOLIO));

		mockMvc.perform(get("/api/buchungen/2026").with(jwt().jwt(token -> token.subject(id.toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void bereichFuerUnbekanntenNutzerVerboten() throws Exception {
		when(komponentenAbfrage.komponentenVon(any())).thenReturn(Set.of());

		mockMvc.perform(get("/api/vertraege").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void anmeldungIstOeffentlich() throws Exception {
		// Ohne Body 400 statt 401: Die Security lässt den Request durch.
		mockMvc.perform(post("/api/anmeldung").with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void abmeldungOhneCsrfTokenAbgelehnt() throws Exception {
		mockMvc.perform(post("/api/abmeldung").with(oidcLogin()))
				.andExpect(status().isForbidden());
	}

	@Test
	void bereichPruefungGiltAuchFuerSessionLogin() throws Exception {
		UUID id = UUID.randomUUID();
		when(komponentenAbfrage.komponentenVon(id)).thenReturn(Set.of(Komponente.VERSORGUNGSVERTRAEGE));

		mockMvc.perform(get("/api/versorgung").with(oidcLogin().idToken(token -> token.subject(id.toString()))))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/portfolio").with(oidcLogin().idToken(token -> token.subject(id.toString()))))
				.andExpect(status().isForbidden());
	}

}
