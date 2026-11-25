package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/vertraege/vorschau}: Prüfungen und Ergebnisse ohne gespeicherten Vertrag. */
@IntegrationTest
class VertragVorschauControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	private TestHaushalt haushalt;

	@BeforeEach
	void haushalt() {
		haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
	}

	private MockHttpServletRequestBuilder vorschau(String body) {
		return post("/api/vertraege/vorschau").with(csrf()).with(haushalt.anmeldung())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	/** Halb ausgefüllte Zeilen ignoriert die Vorschau; ungültige Werte darin lehnt sie ab wie das Speichern. */
	@Test
	void vorschauMitZeilen() throws Exception {
		mockMvc.perform(vorschau("""
						{"kuendbar": true, "zahlweise": "MONATLICH", "beginn": "2025-01-01",
						 "preise": [{"gueltigAb": "2025-01-01", "betrag": {"wert": 10.00, "waehrung": "EUR"}},
						            {"gueltigAb": "2027-01-01", "betrag": null}, {"gueltigAb": "2027-01-01", "betrag": null},
						            {"gueltigAb": null, "betrag": {"wert": 11.00, "waehrung": "EUR"}}],
						 "einmalkosten": [{"bezeichnung": "Router", "betrag": null, "faelligAm": null}],
						 "preissteigerung": {"art": "BETRAG", "rhythmus": "JAEHRLICH"}}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.betrag.wert").value(10.00))
				.andExpect(jsonPath("$.preise", hasSize(1)))
				.andExpect(jsonPath("$.preise[0].art").value("AKTUELL"))
				.andExpect(jsonPath("$.kostenProMonat.wert").value(10.00))
				.andExpect(jsonPath("$.einmalkosten").doesNotExist())
				.andExpect(jsonPath("$.prognose", hasSize(0)));

		mockMvc.perform(vorschau("""
						{"kuendbar": true,
						 "preise": [{"gueltigAb": "2027-01-01", "betrag": {"wert": 0.00, "waehrung": "EUR"}},
						            {"gueltigAb": "2027-01-01", "betrag": {"wert": 11.00, "waehrung": "EUR"}}],
						 "einmalkosten": [{"bezeichnung": "Router", "betrag": {"wert": 5.00, "waehrung": "USD"}}],
						 "preissteigerung": {"art": "PROZENT", "prozent": 101}}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[0].betrag')].code").value("PositiverBetrag"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[1].gueltigAb')].code").value("GueltigAbEindeutig"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'einmalkosten[0].betrag.waehrung')].code")
						.value(containsInAnyOrder("ErlaubteWaehrung", "GleicheWaehrung")))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preissteigerung.prozent')].code").value("DecimalMax"))
				.andExpect(jsonPath("$.felder", hasSize(5)));
	}

	/**
	 * Ohne Beginn rechnet die Vorschau ohne Preise (der erste gilt ab dem Beginn); mit Beginn prüft sie
	 * {@code ErsterPreisAbBeginn} wie das Speichern, sobald eine Zeile vollständig ist.
	 */
	@Test
	void vorschauErsterPreisAbBeginn() throws Exception {
		String preise = """
				"preise": [{"gueltigAb": null, "betrag": {"wert": 10.00, "waehrung": "EUR"}},
				           {"gueltigAb": "2025-06-01", "betrag": {"wert": 11.00, "waehrung": "EUR"}}]""";

		mockMvc.perform(vorschau("{\"kuendbar\": true, \"zahlweise\": \"MONATLICH\", " + preise + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.betrag").doesNotExist())
				.andExpect(jsonPath("$.preise", hasSize(0)));
		mockMvc.perform(vorschau("{\"kuendbar\": true, \"beginn\": \"2025-01-01\", " + preise + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("preise[1].gueltigAb"))
				.andExpect(jsonPath("$.felder[0].code").value("ErsterPreisAbBeginn"))
				.andExpect(jsonPath("$.felder", hasSize(1)));
	}

	@Test
	void vorschauMitFehlendenWerten() throws Exception {
		mockMvc.perform(vorschau("{\"kuendbar\": true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kuendigung.art").value("KEINE_LAUFZEIT"));
	}

	@Test
	void vorschauPrueftWieBeimSpeichern() throws Exception {
		mockMvc.perform(vorschau("""
						{"beginn": "2025-01-01", "laufzeitende": "2024-12-31", "kuendigungsfristMonate": 25}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[*].feld").value(containsInAnyOrder("kuendbar", "laufzeitende",
						"kuendigungsfristMonate")));
		mockMvc.perform(vorschau("{\"kuendbar\": true, \"zahlweise\": \"WOECHENTLICH\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void vorschauMitUnterbrechungen() throws Exception {
		String unterbrechungen = """
				"unterbrechungen": [{"von": "2025-03-01", "bis": "2025-03-31", "art": "KUENDIGUNG"},
				                    {"von": "2025-04-01", "bis": "2025-04-30", "art": "KUENDIGUNG"}]""";

		// Ohne einen Tag Laufzeit dazwischen überschneiden sie sich.
		mockMvc.perform(vorschau("{\"kuendbar\": true, \"beginn\": \"2025-01-01\", " + unterbrechungen + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("unterbrechungen[1].von"))
				.andExpect(jsonPath("$.felder[0].code").value("UnterbrechungenGetrennt"));
		mockMvc.perform(vorschau("{\"kuendbar\": true, \"beginn\": \"2025-03-01\", \"gekuendigtZum\": \"2025-04-30\", "
						+ unterbrechungen.replace("2025-04-01", "2025-04-02") + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[*].code").value(containsInAnyOrder("BeginnVorUnterbrechung",
						"KuendigungNachUnterbrechung")));
	}

}
