package de.bochmann.haushaltsbuch.vertraege.kategorie;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class VertragKategorieControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void liefertStandardkategorienInFesterReihenfolge() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);

		mockMvc.perform(get("/api/vertraege/kategorien").with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].schluessel").value(contains("STREAMING_ABOS", "SOFTWARE_APPS",
						"VERSICHERUNG", "MOBILFUNK_INTERNET", "MITGLIEDSCHAFTEN", "GEBUEHREN_BEITRAEGE", "SONSTIGES")))
				.andExpect(jsonPath("$[2].id").value("7cb7a2b8-38a6-432d-8546-03b3d74f76ec"))
				.andExpect(jsonPath("$[0].name").doesNotExist());
	}

	@Test
	void kategorienAndererHaushalteFehlen() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
		UUID fremde = UUID.randomUUID();
		// Eigene Kategorien kommen erst mit #34; bis dahin direkt in die Tabelle.
		jdbc.sql("INSERT INTO vertrag_kategorie (id, haushalt_id, name, reihenfolge) VALUES (?, ?, 'Fremd', 5)")
				.params(fremde, UUID.randomUUID())
				.update();

		mockMvc.perform(get("/api/vertraege/kategorien").with(haushalt.anmeldung()))
				.andExpect(jsonPath("$[*].id").value(not(hasItem(fremde.toString()))));
	}

	@Test
	void ohneGebuchteKomponenteVerboten() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(get("/api/vertraege/kategorien").with(haushalt.anmeldung()))
				.andExpect(status().isForbidden());
	}

}
