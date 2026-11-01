package de.bochmann.haushaltsbuch.shared.openapi;

import de.bochmann.haushaltsbuch.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI-Beschreibung der API ({@link OpenApiConfig}). Wie die Modul-Doku eingecheckt; die GitHub
 * Action schlägt fehl, wenn {@code docs/openapi.json} nicht dem Build entspricht.
 */
@IntegrationTest
class OpenApiExportTests {

	private static final Path DATEI = Path.of("docs/openapi.json");

	@Autowired
	private MockMvc mockMvc;

	/** Schreibt die Beschreibung nach {@code docs/openapi.json}; ändert sich die API, zeigt der Diff das. */
	@Test
	void schreibtBeschreibung() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Files.writeString(DATEI, json.strip() + "\n");
	}

	@Test
	void beschreibtAnmeldung() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				// Öffentlich, aber schreibend: nur CSRF-Token.
				.andExpect(jsonPath("$.paths['/api/anmeldung'].post.security[0].csrf").exists())
				.andExpect(jsonPath("$.paths['/api/anmeldung'].post.security[0].session").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/anmeldung'].post.responses.401").doesNotExist())
				// Lesend mit Anmeldung: nur Session.
				.andExpect(jsonPath("$.paths['/api/nutzer/ich'].get.security[0].session").exists())
				.andExpect(jsonPath("$.paths['/api/nutzer/ich'].get.security[0].csrf").doesNotExist())
				// Abmeldung kommt aus der Filterkette und antwortet auch ohne Anmeldung mit 204.
				.andExpect(jsonPath("$.paths['/api/abmeldung'].post.security[0].csrf").exists())
				.andExpect(jsonPath("$.paths['/api/abmeldung'].post.responses.204").exists())
				.andExpect(jsonPath("$.paths['/api/abmeldung'].post.responses.401").doesNotExist())
				.andExpect(jsonPath("$.servers[*].url", contains("/")));
	}

	@Test
	void beschreibtFehlerantworten() throws Exception {
		String problem = "#/components/schemas/Problem";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/nutzer/ich'].get.responses.401.content['application/problem+json'].schema.$ref")
						.value(problem))
				.andExpect(jsonPath("$.paths['/api/nutzer/ich'].get.responses.403").exists())
				.andExpect(jsonPath("$.paths['/api/nutzer/ich'].get.responses.400").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/registrierung'].post.responses.400.content['application/problem+json'].schema.$ref")
						.value(problem))
				.andExpect(jsonPath("$.components.schemas.Problem.properties.code").exists())
				.andExpect(jsonPath("$.components.schemas.Problem.properties.felder.items.$ref")
						.value("#/components/schemas/Feldfehler"));
	}

	@Test
	void beschreibtFachendpunkteWieSieAntworten() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/vertraege'].post.responses.201").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege'].post.responses.200").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].get.parameters[0].schema.format").value("uuid"))
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].get.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].get.responses.404").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].get.responses.409").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].put.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].delete.responses.204").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].delete.responses.200").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].delete.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].delete.parameters[?(@.name == 'version')].required")
						.value(contains(true)))
				.andExpect(jsonPath("$.paths['/api/vertraege/{id}'].put.requestBody.content['application/json'].schema.$ref")
						.value("#/components/schemas/VertragAenderung"))
				.andExpect(jsonPath("$.components.schemas.VertragAenderung.required").value(hasItem("version")))
				.andExpect(jsonPath("$.components.schemas.VertragAnfrage.properties.version").doesNotExist())
				// Currency als ISO-Code wie im JSON, nicht als Objekt.
				.andExpect(jsonPath("$.components.schemas.Betrag.properties.waehrung.type").value("string"));
	}

}
