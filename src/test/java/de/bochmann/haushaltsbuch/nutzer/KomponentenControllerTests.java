package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class KomponentenControllerTests {

	/** Standardkategorie {@code VERSICHERUNG} aus der Migration. */
	private static final String VERSICHERUNG = "7cb7a2b8-38a6-432d-8546-03b3d74f76ec";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	@Test
	void liefertGebuchteKomponentenSortiertMitVersion() throws Exception {
		UUID id = UUID.randomUUID();
		haushaltService.anlegen(id, Set.of(Komponente.PORTFOLIO, Komponente.FINANZUEBERSICHT));

		mockMvc.perform(get("/api/nutzer/ich/komponenten")
						.with(oidcLogin().idToken(token -> token.subject(id.toString()))))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"komponenten": ["FINANZUEBERSICHT", "PORTFOLIO"], "version": 0}
						""", true));
	}

	/** Nur nach Fehlern oder Eingriffen von Hand: Konto in Keycloak, aber kein Haushalt bei uns. */
	@Test
	void ohneHaushaltNichtGefunden() throws Exception {
		var ohneHaushalt = oidcLogin().idToken(token -> token.subject(UUID.randomUUID().toString()));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").with(ohneHaushalt))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
		mockMvc.perform(aendern(ohneHaushalt, "[\"PORTFOLIO\"]", 0))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"));
	}

	@Test
	void ohneAnmeldungNichtErlaubt() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich/komponenten")).andExpect(status().isUnauthorized());
	}

	@Test
	void aendertKomponenten() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"VERTRAGSUEBERSICHT\", \"FINANZUEBERSICHT\"]", 0))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"komponenten": ["FINANZUEBERSICHT", "VERTRAGSUEBERSICHT"], "version": 1}
						""", true));
		mockMvc.perform(get("/api/nutzer/ich/komponenten").with(haushalt.anmeldung()))
				.andExpect(content().json("""
						{"komponenten": ["FINANZUEBERSICHT", "VERTRAGSUEBERSICHT"], "version": 1}
						""", true));
	}

	/** Ausschalten sperrt nur den Zugriff; nach dem Einschalten sind die Verträge wieder da. */
	@Test
	void ausschaltenSperrtZugriffUndBehaeltDaten() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT, Komponente.PORTFOLIO);
		mockMvc.perform(post("/api/vertraege").with(csrf()).with(haushalt.anmeldung())
						.contentType(MediaType.APPLICATION_JSON)
						.content(vertrag()))
				.andExpect(status().isCreated());

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"PORTFOLIO\"]", 0)).andExpect(status().isOk());
		mockMvc.perform(get("/api/vertraege").with(haushalt.anmeldung()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("KEIN_ZUGRIFF"));

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"PORTFOLIO\", \"VERTRAGSUEBERSICHT\"]", 1))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/vertraege").with(haushalt.anmeldung()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].anbieter").value("Versicherer"));
	}

	@Test
	void veralteteVersionKonflikt() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);
		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"FINANZUEBERSICHT\"]", 0)).andExpect(status().isOk());

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"VERTRAGSUEBERSICHT\"]", 0))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("KONFLIKT"));
	}

	@Test
	void gleicheAuswahlLaesstVersion() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"PORTFOLIO\"]", 0))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void ohneKomponenteUngueltig() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(aendern(haushalt.anmeldung(), "[]", 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("komponenten"))
				.andExpect(jsonPath("$.felder[0].code").value("NotEmpty"));
	}

	/** {@code null} in der Menge lehnt schon Jackson beim Lesen ab, nicht erst die Validierung. */
	@Test
	void nullInDerListeUngueltig() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"PORTFOLIO\", null]", 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void unbekannteKomponenteUngueltig() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(aendern(haushalt.anmeldung(), "[\"KALENDER\"]", 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void ohneVersionUngueltig() throws Exception {
		var haushalt = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);

		mockMvc.perform(put("/api/nutzer/ich/komponenten").with(csrf()).with(haushalt.anmeldung())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"komponenten\": [\"PORTFOLIO\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.felder[0].feld").value("version"))
				.andExpect(jsonPath("$.felder[0].code").value("NotNull"));
	}

	private static MockHttpServletRequestBuilder aendern(
			RequestPostProcessor anmeldung, String komponenten,
			long version) {
		return put("/api/nutzer/ich/komponenten").with(csrf()).with(anmeldung)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"komponenten\": %s, \"version\": %d}".formatted(komponenten, version));
	}

	private static String vertrag() {
		return """
				{"anbieter": "Versicherer", "bezeichnung": null, "kategorieId": "%s", "vertragsnummer": null,
				 "preise": [{"gueltigAb": "2026-01-01", "betrag": {"wert": 10.00, "waehrung": "EUR"}}], "zahlweise": "MONATLICH",
				 "zahlungsweg": "LASTSCHRIFT", "einmalkosten": [], "preissteigerung": null, "preisgarantieBis": null,
				 "erinnerungSonderkuendigung": false, "kuendbar": true, "beginn": "2026-01-01", "laufzeitende": null,
				 "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": 0, "gekuendigtZum": null,
				 "notiz": null}
				""".formatted(VERSICHERUNG);
	}

}
