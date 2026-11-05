package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	/** Mock aus {@code @IntegrationTest}, meldet ohne eigene Vorgabe {@code UP}. */
	@Autowired
	@Qualifier("keycloakHealthIndicator")
	private HealthIndicator keycloakHealthIndicator;

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
	void infoIsPublicWithVersionBuildTimeAndStage() throws Exception {
		mockMvc.perform(get("/actuator/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.build.version").isNotEmpty())
				.andExpect(jsonPath("$.build.time", not(startsWith("1980"))))
				.andExpect(jsonPath("$.build.length()").value(2))
				.andExpect(jsonPath("$.app.stage").value("DEV"));
	}

	@Test
	void infoNamesOnlyCommitBesideBuildAndStage() throws Exception {
		// Ohne .git (z. B. Quellarchiv) bleibt git.properties leer; in der CI ist der Commit immer da.
		assumeFalse(PropertiesLoaderUtils.loadProperties(new ClassPathResource("git.properties")).isEmpty());

		mockMvc.perform(get("/actuator/info"))
				.andExpect(jsonPath("$.git.commit.id").isNotEmpty())
				.andExpect(jsonPath("$.git.commit.length()").value(1))
				.andExpect(jsonPath("$.git.length()").value(1))
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void openApiIsPublicInDev() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/buchungen"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NICHT_ANGEMELDET"));
	}

	@Test
	void bereichMitGebuchterKomponenteErlaubt() throws Exception {
		UUID id = UUID.randomUUID();
		haushaltService.anlegen(id, Set.of(Komponente.PORTFOLIO));

		// Noch kein Controller unter /api/portfolio: 404 heißt, die Security hat durchgelassen.
		mockMvc.perform(get("/api/portfolio").with(anmeldung(id)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void bereichOhneGebuchteKomponenteVerboten() throws Exception {
		UUID id = UUID.randomUUID();
		haushaltService.anlegen(id, Set.of(Komponente.PORTFOLIO));

		mockMvc.perform(get("/api/buchungen/2026").with(anmeldung(id)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
	}

	@Test
	void bereichFuerUnbekanntenNutzerVerboten() throws Exception {
		mockMvc.perform(get("/api/vertraege").with(anmeldung(UUID.randomUUID())))
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
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_UNGUELTIG"));
	}

	@Test
	void abmeldungOhneAnmeldungErfolgreich() throws Exception {
		// Der Logout-Filter läuft vor der Zugriffsprüfung; die SPA muss den Fall nicht unterscheiden.
		mockMvc.perform(post("/api/abmeldung").with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void bearerTokenWirdNichtAngenommen() throws Exception {
		// Angemeldet wird nur per Session; ein Bearer-Token zählt nicht als Anmeldung.
		mockMvc.perform(get("/api/nutzer/ich").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.e30.c2ln"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NICHT_ANGEMELDET"));
	}

	private static RequestPostProcessor anmeldung(UUID nutzerId) {
		return oidcLogin().idToken(token -> token.subject(nutzerId.toString()));
	}

}
