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
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
				 "preise": [{"gueltigAb": null, "betrag": {"wert": %s, "waehrung": "EUR"}}], "zahlweise": "MONATLICH",
				 "abbuchungAb": "%s",
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
				.andExpect(jsonPath("$.preise[0].gueltigAb").doesNotExist())
				.andExpect(jsonPath("$.preise[0].betrag.wert").value(13.99))
				// Ohne Beginn zwölf Abbuchungen im Jahr, auch die vor dem Anker.
				.andExpect(jsonPath("$.berechnet.kostenImJahr.wert").value(167.88))
				.andExpect(jsonPath("$.berechnet.jahr").value(heute.getYear()))
				.andExpect(jsonPath("$.berechnet.naechsteAbbuchung.am").value(heute.plusDays(9).toString()))
				.andExpect(jsonPath("$.berechnet.naechsteAbbuchung.betrag.wert").value(13.99))
				.andExpect(jsonPath("$.berechnet.naechstePreisaenderung").doesNotExist())
				.andExpect(jsonPath("$.berechnet.kuendigung.art").value("MONATLICH_KUENDBAR"))
				.andExpect(jsonPath("$.berechnet.kuendigung.zum").value(heute.plusDays(8).toString()))
				.andReturn().getResponse();

		String id = JsonPath.read(antwort.getContentAsString(), "$.id");
		assertThat(antwort.getHeader("Location")).endsWith("/api/vertraege/" + id);
		assertThat(jdbc.sql("SELECT haushalt_id FROM vertrag WHERE id = ?").param(UUID.fromString(id))
				.query(UUID.class).single()).isEqualTo(haushalt.haushaltId());
	}

	/** Legt ein Abo im Haushalt des Tests an und liefert die Antwort als JSON. */
	private String angelegt() throws Exception {
		return mockMvc.perform(anlegen(abo("13.99", null, null, 0)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void ruftEigenenVertragAb() throws Exception {
		String angelegt = angelegt();
		String id = JsonPath.read(angelegt, "$.id");

		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(content().json(angelegt, true));
	}

	/** Pflicht-Test zur Trennung der Haushalte: Ein fremder Vertrag ist für andere „nicht gefunden“. */
	@Test
	void vertragEinesAnderenHaushaltsNichtGefunden() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(get("/api/vertraege/{id}", id).with(fremder.anmeldung()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void unbekannterVertragNichtGefunden() throws Exception {
		mockMvc.perform(get("/api/vertraege/{id}", UUID.randomUUID()).with(haushalt.anmeldung()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void idOhneUuidFormatUngueltig() throws Exception {
		mockMvc.perform(get("/api/vertraege/{id}", "42").with(haushalt.anmeldung()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void abrufenOhneGebuchteKomponenteVerboten() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var ohne = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(get("/api/vertraege/{id}", id).with(ohne.anmeldung()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
	}

	@Test
	void listetVertraegeMitAllenAngaben() throws Exception {
		String angelegt = angelegt();

		mockMvc.perform(get("/api/vertraege").with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(content().json("[" + angelegt + "]", true));
	}

	/** Pflicht-Test zur Trennung der Haushalte: Fremde Verträge stehen nicht in der Liste. */
	@Test
	void listeOhneVertraegeAndererHaushalte() throws Exception {
		angelegt();
		var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(get("/api/vertraege").with(fremder.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void auflistenOhneGebuchteKomponenteVerboten() throws Exception {
		var ohne = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(get("/api/vertraege").with(ohne.anmeldung()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
	}

	@Test
	void uebersichtPasstZurListe() throws Exception {
		String angelegt = mockMvc.perform(anlegen(abo("13.99", null, heute.plusDays(100).toString(), 1)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/vertraege/uebersicht").with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"laufendeVertraege": 1,
						 "kostenProMonat": [{"wert": 13.99, "waehrung": "EUR"}],
						 "kostenImJahr": [{"wert": 167.88, "waehrung": "EUR"}], "jahr": %d,
						 "naechsteKuendigungsfristen": [{"id": "%s", "anbieter": "Streamingdienst", "bezeichnung": null,
						   "betrag": {"wert": 13.99, "waehrung": "EUR"}, "zahlweise": "MONATLICH",
						   "kuendigenBis": "%s", "zum": "%s", "dringend": %s}]}
						""".formatted(heute.getYear(), JsonPath.read(angelegt, "$.id"),
						JsonPath.read(angelegt, "$.berechnet.kuendigung.kuendigenBis"), heute.plusDays(100),
						JsonPath.read(angelegt, "$.berechnet.kuendigung.dringend")), true));
	}

	/** Pflicht-Test zur Trennung der Haushalte: Fremde Verträge zählen nicht mit. */
	@Test
	void uebersichtOhneVertraegeAndererHaushalte() throws Exception {
		angelegt();
		var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(get("/api/vertraege/uebersicht").with(fremder.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"laufendeVertraege": 0, "kostenProMonat": [], "kostenImJahr": [], "jahr": %d,
						 "naechsteKuendigungsfristen": []}
						""".formatted(heute.getYear()), true));
	}

	/** Die Angaben des Abos aus {@link #abo} als Änderung: {@code version} und andere Werte. */
	private String aenderung(Object version, String betrag) {
		return abo(betrag, null, null, 0).replaceFirst("\\{", "{\"version\": " + version + ", ");
	}

	private MockHttpServletRequestBuilder aendern(String id, String body) {
		return put("/api/vertraege/{id}", id).with(csrf()).with(haushalt.anmeldung())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	@Test
	void aendertVertragUndAntwortetMitNeuenAngaben() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");

		String geaendert = mockMvc.perform(aendern(id, aenderung(0, "20.00")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.betrag.wert").value(20.00))
				.andExpect(jsonPath("$.berechnet.kostenImJahr.wert").value(240.00))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(content().json(geaendert, true));
	}

	/** Die SPA darf die Antwort samt {@code id}, {@code kategorie} und {@code berechnet} zurückschicken. */
	@Test
	void ignoriertMitgeschickteFelderDerAntwort() throws Exception {
		String angelegt = angelegt();
		String id = JsonPath.read(angelegt, "$.id");
		String zurueck = angelegt.replaceFirst("\\{", "{\"kategorieId\": \"" + VertragServiceTests.VERSICHERUNG + "\", ")
				.replace("\"Streamingdienst\"", "\"Videodienst\"");

		mockMvc.perform(aendern(id, zurueck))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.anbieter").value("Videodienst"))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void veralteteVersionKonflikt() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		mockMvc.perform(aendern(id, aenderung(0, "20.00"))).andExpect(status().isOk());

		mockMvc.perform(aendern(id, aenderung(0, "30.00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("KONFLIKT"));
	}

	@Test
	void aendernOhneVersionUngueltig() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");

		mockMvc.perform(aendern(id, abo("20.00", null, null, 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("version"))
				.andExpect(jsonPath("$.felder[0].code").value("NotNull"));
	}

	/** Pflicht-Test zur Trennung der Haushalte: Einen fremden Vertrag kann niemand ändern. */
	@Test
	void vertragEinesAnderenHaushaltsAendernNichtGefunden() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(put("/api/vertraege/{id}", id).with(csrf()).with(fremder.anmeldung())
						.contentType(MediaType.APPLICATION_JSON)
						.content(aenderung(0, "1.00")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(jsonPath("$.betrag.wert").value(13.99))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void unbekanntenVertragAendernNichtGefunden() throws Exception {
		mockMvc.perform(aendern(UUID.randomUUID().toString(), aenderung(0, "20.00")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void aendernMitIdOhneUuidFormatUngueltig() throws Exception {
		mockMvc.perform(aendern("42", aenderung(0, "20.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void aendernOhneGebuchteKomponenteVerboten() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var ohne = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(put("/api/vertraege/{id}", id).with(csrf()).with(ohne.anmeldung())
						.contentType(MediaType.APPLICATION_JSON)
						.content(aenderung(0, "20.00")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
	}

	private MockHttpServletRequestBuilder loeschen(String id, Object version, TestHaushalt als) {
		var anfrage = delete("/api/vertraege/{id}", id).with(csrf()).with(als.anmeldung());
		return version == null ? anfrage : anfrage.param("version", String.valueOf(version));
	}

	@Test
	void loeschtVertrag() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");

		mockMvc.perform(loeschen(id, 0, haushalt))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/vertraege").with(haushalt.anmeldung()))
				.andExpect(jsonPath("$", hasSize(0)));
		// Zweites Löschen: schon weg.
		mockMvc.perform(loeschen(id, 0, haushalt))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void loeschenMitVeralteterVersionKonflikt() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		mockMvc.perform(aendern(id, aenderung(0, "20.00"))).andExpect(status().isOk());

		mockMvc.perform(loeschen(id, 0, haushalt))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("KONFLIKT"));
		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.betrag.wert").value(20.00));
	}

	@Test
	void loeschenOhneOderMitUngueltigerVersionUngueltig() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");

		mockMvc.perform(loeschen(id, null, haushalt))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
		mockMvc.perform(loeschen(id, "abc", haushalt))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isOk());
	}

	/** Pflicht-Test zur Trennung der Haushalte: Einen fremden Vertrag kann niemand löschen. */
	@Test
	void vertragEinesAnderenHaushaltsLoeschenNichtGefunden() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(loeschen(id, 0, fremder))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isOk());
	}

	@Test
	void unbekanntenVertragLoeschenNichtGefunden() throws Exception {
		mockMvc.perform(loeschen(UUID.randomUUID().toString(), 0, haushalt))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void loeschenMitIdOhneUuidFormatUngueltig() throws Exception {
		mockMvc.perform(loeschen("42", 0, haushalt))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void loeschenOhneGebuchteKomponenteVerboten() throws Exception {
		String id = JsonPath.read(angelegt(), "$.id");
		var ohne = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(loeschen(id, 0, ohne))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));
		mockMvc.perform(get("/api/vertraege/{id}", id).with(haushalt.anmeldung()))
				.andExpect(status().isOk());
	}

	@Test
	void prueftFelder() throws Exception {
		mockMvc.perform(anlegen(abo("0.00", "2026-05-01", "2026-04-30", 25)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[0].betrag')].code").value("PositiverBetrag"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'laufzeitende')].code").value("LaufzeitendeNachBeginn"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'kuendigungsfristMonate')].code").value("Max"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'kuendigungsfristMonate')].parameter.value").value(24));
	}

	/** Die Regeln der Preisliste; die Fehler hängen an der Zeile in der Reihenfolge der Anfrage. */
	@Test
	void prueftPreise() throws Exception {
		String preise = """
				"preise": [{"gueltigAb": null, "betrag": {"wert": 10.00, "waehrung": "EUR"}},
				           {"gueltigAb": null, "betrag": {"wert": 11.00, "waehrung": "EUR"}},
				           {"gueltigAb": "2026-01-01", "betrag": {"wert": 12.00, "waehrung": "EUR"}},
				           {"gueltigAb": "2026-01-01", "betrag": {"wert": 13.00, "waehrung": "EUR"}},
				           {"gueltigAb": "2019-01-01", "betrag": {"wert": 14.00, "waehrung": "USD"}}],""";

		mockMvc.perform(anlegen(abo("10.00", "2020-01-01", null, 0).replaceFirst("\"preise\": \\[.*?\\],", preise)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[1].gueltigAb')].code").value("GueltigAbPflicht"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[3].gueltigAb')].code").value("GueltigAbEindeutig"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[4].gueltigAb')].code").value("PreisNichtVorBeginn"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise[4].betrag.waehrung')].code")
						.value(containsInAnyOrder("GleicheWaehrung", "ErlaubteWaehrung")))
				.andExpect(jsonPath("$.felder", hasSize(5)));
	}

	@Test
	void preiseNichtLeerUndBegrenzt() throws Exception {
		String preis = "{\"gueltigAb\": null, \"betrag\": {\"wert\": 10.00, \"waehrung\": \"EUR\"}}";

		mockMvc.perform(anlegen(abo("10.00", null, null, 0).replaceFirst("\"preise\": \\[.*?\\],", "\"preise\": [],")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("preise"))
				.andExpect(jsonPath("$.felder[0].code").value("NotEmpty"));
		// 101 Einträge; dass alle ohne Datum sind, meldet zusätzlich GueltigAbPflicht.
		mockMvc.perform(anlegen(abo("10.00", null, null, 0).replaceFirst("\"preise\": \\[.*?\\],",
						"\"preise\": [" + String.join(",", Collections.nCopies(101, preis)) + "],")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise')].code").value("Size"))
				.andExpect(jsonPath("$.felder[?(@.feld == 'preise')].parameter.max").value(100));
	}

	/** Ohne Grenze gäbe es einen Überlauf beim Weiterzählen bzw. Postgres könnte nicht speichern: 500 statt 400. */
	@Test
	void extremeDatenUngueltig() throws Exception {
		mockMvc.perform(anlegen(abo("13.99", "1899-12-31", "+6000000-01-01", 1)
						.replace("\"abbuchungAb\": \"" + heute.plusDays(9), "\"abbuchungAb\": \"+6000000-01-01")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[*].feld").value(containsInAnyOrder("abbuchungAb", "beginn", "laufzeitende")))
				.andExpect(jsonPath("$.felder[*].code").value(containsInAnyOrder("PlausiblesDatum", "PlausiblesDatum",
						"PlausiblesDatum")));

		String id = JsonPath.read(angelegt(), "$.id");
		mockMvc.perform(aendern(id, aenderung(0, "20.00").replace("\"gekuendigtZum\": null",
						"\"gekuendigtZum\": \"2200-01-01\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("gekuendigtZum"))
				.andExpect(jsonPath("$.felder[0].code").value("PlausiblesDatum"));

		mockMvc.perform(vorschau("""
						{"kuendbar": true, "beginn": "+999999999-12-31", "verlaengerung": "ZWOELF_MONATE"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("beginn"))
				.andExpect(jsonPath("$.felder[0].code").value("PlausiblesDatum"));
	}

	private MockHttpServletRequestBuilder vorschau(String body) {
		return post("/api/vertraege/vorschau").with(csrf()).with(haushalt.anmeldung())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	@Test
	void vorschauBerechnetKuendigungOhneZuSpeichern() throws Exception {
		String angelegt = mockMvc.perform(anlegen(abo("13.99", null, heute.plusDays(100).toString(), 1)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		// Dieselben Werte (überzählige Felder ignoriert das Backend) ergeben dieselbe Kündigung.
		mockMvc.perform(vorschau(abo("13.99", null, heute.plusDays(100).toString(), 1)))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(content().json("""
						{"kuendigung": {"art": "KUENDIGEN_BIS", "kuendigenBis": "%s", "zum": "%s", "fristMonate": 1,
						 "dringend": %s}}
						""".formatted(JsonPath.read(angelegt, "$.berechnet.kuendigung.kuendigenBis"), heute.plusDays(100),
						JsonPath.read(angelegt, "$.berechnet.kuendigung.dringend")), true));
		assertThat(jdbc.sql("SELECT count(*) FROM vertrag WHERE haushalt_id = ?").param(haushalt.haushaltId())
				.query(Long.class)
				.single()).isEqualTo(1);
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
	void pflichtfelder() throws Exception {
		mockMvc.perform(anlegen("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[*].feld").value(containsInAnyOrder(
						"anbieter", "kategorieId", "preise", "zahlweise", "abbuchungAb", "kuendbar")));
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
