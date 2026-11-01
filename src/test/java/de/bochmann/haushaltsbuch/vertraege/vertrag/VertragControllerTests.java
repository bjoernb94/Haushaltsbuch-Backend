package de.bochmann.haushaltsbuch.vertraege.vertrag;

import com.jayway.jsonpath.JsonPath;
import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class VertragControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private Clock clock;

	private TestHaushalt haushalt;

	private LocalDate heute;

	@BeforeEach
	void haushalt() {
		haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
		heute = LocalDate.now(clock);
	}

	/** Monatliches Abo ohne Laufzeitende, jederzeit zum Ende des Zahlungsintervalls kündbar. */
	private String abo(String betrag, String beginn, String laufzeitende, Object frist) {
		return """
				{"anbieter": "Streamingdienst", "bezeichnung": "", "kategorieId": "%s", "vertragsnummer": null,
				 "betrag": {"wert": %s, "waehrung": "EUR"}, "zahlweise": "MONATLICH", "abbuchungAb": "%s",
				 "zahlungsweg": "KREDITKARTE", "kuendbar": true, "beginn": %s, "laufzeitende": %s,
				 "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": %s, "gekuendigtZum": null,
				 "notiz": "Familien-Abo"}
				""".formatted(VertragServiceTests.VERSICHERUNG, betrag, heute.plusDays(9), json(beginn),
				json(laufzeitende), frist);
	}

	private static String json(String datum) {
		return datum == null ? "null" : "\"" + datum + "\"";
	}

	private MockHttpServletRequestBuilder anlegen(String body) {
		return post("/api/vertraege").with(csrf()).with(haushalt.anmeldung())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	@Test
	void legtVertragAnUndAntwortetMitBerechnetenAngaben() throws Exception {
		var antwort = mockMvc.perform(anlegen(abo("13.99", null, null, 0)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.anbieter").value("Streamingdienst"))
				.andExpect(jsonPath("$.bezeichnung").doesNotExist())
				.andExpect(jsonPath("$.kategorie.schluessel").value("VERSICHERUNG"))
				.andExpect(jsonPath("$.betrag.wert").value(13.99))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.status").doesNotExist())
				.andExpect(jsonPath("$.berechnet.status").value("AKTIV"))
				.andExpect(jsonPath("$.berechnet.kostenProMonat.wert").value(13.99))
				.andExpect(jsonPath("$.berechnet.kostenProJahr.wert").value(167.88))
				.andExpect(jsonPath("$.berechnet.naechsteAbbuchung").value(heute.plusDays(9).toString()))
				.andExpect(jsonPath("$.berechnet.kuendigung.art").value("MONATLICH_KUENDBAR"))
				.andExpect(jsonPath("$.berechnet.kuendigung.zum").value(heute.plusDays(8).toString()))
				.andReturn().getResponse();

		String id = JsonPath.read(antwort.getContentAsString(), "$.id");
		assertThat(antwort.getHeader("Location")).endsWith("/api/vertraege/" + id);
		assertThat(jdbc.sql("SELECT haushalt_id FROM vertrag WHERE id = ?").param(UUID.fromString(id))
				.query(UUID.class).single()).isEqualTo(haushalt.haushaltId());
	}

	@Test
	void prueftFelder() throws Exception {
		mockMvc.perform(anlegen(abo("0.00", "2026-05-01", "2026-04-30", 25)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'betrag')].code").value("PositiverBetrag"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'laufzeitende')].code").value("LaufzeitendeNachBeginn"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'kuendigungsfristMonate')].code").value("Max"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'kuendigungsfristMonate')].parameter.value").value(24));
	}

	@Test
	void pflichtfelder() throws Exception {
		mockMvc.perform(anlegen("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[*].feld").value(containsInAnyOrder(
						"anbieter", "kategorieId", "betrag", "zahlweise", "abbuchungAb", "kuendbar")));
	}

	@Test
	void unbekannteKategorie() throws Exception {
		mockMvc.perform(anlegen(abo("13.99", null, null, 0).replace(VertragServiceTests.VERSICHERUNG.toString(),
						UUID.randomUUID().toString())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("kategorieId"))
				.andExpect(jsonPath("$.felder[0].code").value("Unbekannt"));
	}

	@Test
	void ohneGebuchteKomponenteVerboten() throws Exception {
		var ohne = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);
		mockMvc.perform(post("/api/vertraege").with(csrf()).with(ohne.anmeldung())
						.contentType(MediaType.APPLICATION_JSON)
						.content(abo("13.99", null, null, 0)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
	}

}
