package de.bochmann.haushaltsbuch.shared.openapi;

import com.jayway.jsonpath.JsonPath;
import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI-Beschreibung der API ({@link OpenApiConfig}). Wie die Modul-Doku eingecheckt; die GitHub
 * Action schlägt fehl, wenn {@code docs/openapi.json} nicht dem Build entspricht.
 */
@IntegrationTest
@SuppressWarnings("unchecked")
class OpenApiExportTests {

	private static final Path DATEI = Path.of("docs/openapi.json");

	/** Standardkategorie {@code VERSICHERUNG} aus der Migration. */
	private static final String KATEGORIE = "7cb7a2b8-38a6-432d-8546-03b3d74f76ec";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private Clock clock;

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
						.value("#/components/schemas/Feldfehler"))
				// Sonst erzeugt openapi-typescript Record<string, never> statt beliebiger Attribute.
				.andExpect(jsonPath("$.components.schemas.Feldfehler.properties.parameter.additionalProperties").value(true));
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

	@Test
	void beschreibtEnumsAlsEigeneSchemas() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.components.schemas.Zahlweise.enum").isArray())
				.andExpect(jsonPath("$.components.schemas.Komponente.enum").isArray())
				.andExpect(jsonPath("$.components.schemas.VertragAntwort.properties.zahlweise.$ref")
						.value("#/components/schemas/Zahlweise"))
				// Nullable: Verweis oder null.
				.andExpect(jsonPath("$.components.schemas.VertragAntwort.properties.zahlungsweg.oneOf[0].$ref")
						.value("#/components/schemas/Zahlungsweg"))
				.andExpect(jsonPath("$.components.schemas.VertragAntwort.properties.zahlungsweg.oneOf[1].type").value("null"))
				.andExpect(jsonPath("$.components.schemas.VertragKategorieSchluessel.enum").value(hasItem("VERSICHERUNG")));
	}

	@Test
	void inErfolgsantwortenIstJedesFeldPflicht() throws Exception {
		Map<String, Object> api = beschreibung();
		Set<String> namen = new TreeSet<>();
		for (Map<String, Object> eintrag : JsonPath.<List<Map<String, Object>>>read(api, "$.paths[*][*].responses")) {
			eintrag.forEach((status, antwort) -> {
				if (status.startsWith("2")) {
					sammleSchemas(antwort, schemas(api), namen);
				}
			});
		}

		assertThat(namen).contains("VertragAntwort", "VertragBerechnet", "Kuendigung", "AnstehendeKuendigung", "Betrag",
				"Profil", "VertragKategorieAntwort").doesNotContain("Problem", "Feldfehler");
		for (String name : namen) {
			if (schemas(api).get(name).get("properties") instanceof Map<?, ?> felder) {
				assertThat((List<String>) schemas(api).get(name).get("required")).as(name)
						.containsExactlyInAnyOrderElementsOf((Set<String>) felder.keySet());
			}
		}
	}

	/** Betrag steckt auch in Anfragen; seine Felder sind dort Pflicht, denn der Konstruktor lehnt null ab. */
	@Test
	void inAnfragenIstNullableWasNichtPflichtIst() throws Exception {
		Map<String, Object> api = beschreibung();
		Set<String> namen = new TreeSet<>();
		sammleSchemas(JsonPath.read(api, "$.paths[*][*].requestBody"), schemas(api), namen);

		assertThat(namen).contains("VertragAnfrage", "VertragAenderung", "VertragVorschauAnfrage", "Betrag");
		for (String name : namen) {
			if (schemas(api).get(name).get("properties") instanceof Map<?, ?> felder) {
				var pflicht = (List<String>) schemas(api).get(name).getOrDefault("required", List.of());
				felder.forEach((feld, schema) -> assertThat(nullable((Map<String, Object>) schema)).as(name + "." + feld)
						.isEqualTo(!pflicht.contains(feld)));
			}
		}
	}

	/** Fällt auf, wenn an einem Feld {@code @Nullable} vergessen wurde: Die Beschreibung wäre dann falsch. */
	@Test
	void jedesNullInEinerAntwortIstBeschrieben() throws Exception {
		Map<String, Object> api = beschreibung();
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
		LocalDate heute = LocalDate.now(clock);
		String nurPflichtfelder = """
				{"anbieter": "Streamingdienst", "kategorieId": "%s",
				 "preise": [{"betrag": {"wert": 10, "waehrung": "EUR"}}],
				 "zahlweise": "MONATLICH", "abbuchungAb": "%s", "einmalkosten": [], "erinnerungSonderkuendigung": true,
				 "kuendbar": true%s}
				""";

		for (String zusatz : List.of("",
				// Beendet: keine nächste Abbuchung.
				", \"gekuendigtZum\": \"%s\"".formatted(heute.minusDays(1)),
				// Mit Kündigungsfrist, erscheint auch in der Übersicht.
				", \"laufzeitende\": \"%s\", \"verlaengerung\": \"ZWOELF_MONATE\"".formatted(heute.plusMonths(6)),
				// Mit Preissteigerung in Prozent (ohne Betrag) und Garantie: Prognose und Preisverlauf.
				", \"preissteigerung\": {\"art\": \"PROZENT\", \"prozent\": 2.5, \"rhythmus\": \"JAEHRLICH\", \"naechsteAm\": \"%s\"}, \"preisgarantieBis\": \"%s\""
						.formatted(heute.plusMonths(2), heute.plusMonths(1)))) {
			String antwort = mockMvc.perform(post("/api/vertraege").with(csrf()).with(haushalt.anmeldung())
							.contentType(MediaType.APPLICATION_JSON)
							.content(nurPflichtfelder.formatted(KATEGORIE, heute.plusDays(9), zusatz)))
					.andExpect(status().isCreated())
					.andReturn().getResponse().getContentAsString();
			pruefeNull(JsonPath.read(antwort, "$"), Map.of("$ref", "#/components/schemas/VertragAntwort"), schemas(api),
					"VertragAntwort");
		}
		String uebersicht = mockMvc.perform(get("/api/vertraege/uebersicht").with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				// Die Frist muss wirklich dabei sein, sonst prüft der Test AnstehendeKuendigung nicht.
				.andExpect(jsonPath("$.naechsteKuendigungsfristen.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		pruefeNull(JsonPath.read(uebersicht, "$"), Map.of("$ref", "#/components/schemas/VertragsuebersichtAntwort"),
				schemas(api), "VertragsuebersichtAntwort");
		// Die Vorschau eines leeren und eines halb ausgefüllten Formulars: fast alles null.
		for (String formular : List.of("{\"kuendbar\": true}", """
				{"kuendbar": true, "zahlweise": "MONATLICH", "anteilProzent": 50,
				 "preise": [{"betrag": {"wert": 10, "waehrung": "EUR"}}]}""")) {
			String vorschau = mockMvc.perform(post("/api/vertraege/vorschau").with(csrf()).with(haushalt.anmeldung())
							.contentType(MediaType.APPLICATION_JSON)
							.content(formular))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			pruefeNull(JsonPath.read(vorschau, "$"), Map.of("$ref", "#/components/schemas/VertragVorschauAntwort"),
					schemas(api), "VertragVorschauAntwort");
		}
	}

	private Map<String, Object> beschreibung() throws Exception {
		return JsonPath.read(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(), "$");
	}

	private static Map<String, Map<String, Object>> schemas(Map<String, Object> api) {
		return JsonPath.read(api, "$.components.schemas");
	}

	private static String refName(Object ref) {
		return ((String) ref).substring(((String) ref).lastIndexOf('/') + 1);
	}

	/** Alle Schemas, die von {@code knoten} aus über {@code $ref} erreichbar sind. */
	private static void sammleSchemas(Object knoten, Map<String, Map<String, Object>> schemas, Set<String> namen) {
		if (knoten instanceof List<?> liste) {
			liste.forEach(wert -> sammleSchemas(wert, schemas, namen));
		}
		else if (knoten instanceof Map<?, ?> objekt) {
			objekt.forEach((schluessel, wert) -> {
				if ("$ref".equals(schluessel)) {
					if (namen.add(refName(wert))) {
						sammleSchemas(schemas.get(refName(wert)), schemas, namen);
					}
				}
				else {
					sammleSchemas(wert, schemas, namen);
				}
			});
		}
	}

	private static boolean nullable(Map<String, Object> schema) {
		return schema.get("type") instanceof List<?> typen && typen.contains("null")
				|| schema.get("oneOf") instanceof List<?> varianten
						&& varianten.stream().anyMatch(variante -> "null".equals(((Map<?, ?>) variante).get("type")));
	}

	/** Geht die Antwort und ihr Schema gemeinsam durch: Jedes Feld ist beschrieben, jedes {@code null} nullable. */
	private static void pruefeNull(Object wert, Map<String, Object> schema, Map<String, Map<String, Object>> schemas,
			String pfad) {
		if (schema.get("$ref") != null) {
			pruefeNull(wert, schemas.get(refName(schema.get("$ref"))), schemas, pfad);
		}
		else if (wert == null) {
			assertThat(nullable(schema)).as(pfad + " ist null, aber nicht nullable beschrieben").isTrue();
		}
		else if (schema.get("oneOf") instanceof List<?> varianten) {
			pruefeNull(wert, (Map<String, Object>) varianten.getFirst(), schemas, pfad);
		}
		else if (wert instanceof List<?> liste) {
			liste.forEach(eintrag -> pruefeNull(eintrag, (Map<String, Object>) schema.get("items"), schemas, pfad + "[]"));
		}
		else if (wert instanceof Map<?, ?> objekt) {
			var felder = (Map<String, Map<String, Object>>) schema.get("properties");
			objekt.forEach((name, feld) -> {
				assertThat(felder).as(pfad).containsKey((String) name);
				pruefeNull(feld, felder.get(name), schemas, pfad + "." + name);
			});
		}
	}

}
