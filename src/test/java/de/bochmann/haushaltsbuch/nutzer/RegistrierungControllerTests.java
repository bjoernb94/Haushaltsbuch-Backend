package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.Anmeldung;
import de.bochmann.haushaltsbuch.shared.security.GemockteAnmeldung;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class RegistrierungControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private NutzerRepository nutzerRepository;

	@Autowired
	private KomponentenAbfrage komponentenAbfrage;

	@Autowired
	private RegistrierungService registrierungService;

	@Autowired
	private Identitaetsanbieter identitaetsanbieter;

	/** Spy aus {@code @IntegrationTest}. */
	@Autowired
	private Anmeldung anmeldung;

	/** Danach direkt angemeldet: Dieselbe Session sieht die gewählten Komponenten des neuen Nutzers. */
	@Test
	void registriertUndMeldetAn() throws Exception {
		UUID id = UUID.randomUUID();
		when(identitaetsanbieter.benutzerAnlegen("max@example.org", "geheim123", "Max", "Muster"))
				.thenReturn(id);
		GemockteAnmeldung.nimmtAn(context, "max@example.org", "geheim123", id);

		MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("max@example.org", "[\"FINANZUEBERSICHT\", \"PORTFOLIO\"]")))
				.andExpect(status().isCreated())
				.andReturn().getRequest().getSession(false);

		assertThat(session).isNotNull();
		mockMvc.perform(get("/api/nutzer/ich/komponenten").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").value(containsInAnyOrder("FINANZUEBERSICHT", "PORTFOLIO")));

		assertThat(nutzerRepository.findById(id)).hasValueSatisfying(nutzer ->
				assertThat(nutzer.getAngelegtAm()).isNotNull());
		// Die Komponenten gehören dem bei der Registrierung angelegten Haushalt.
		assertThat(komponentenAbfrage.komponentenVon(id))
				.containsExactlyInAnyOrder(Komponente.FINANZUEBERSICHT, Komponente.PORTFOLIO);
		verify(anmeldung).anmelden(eq("max@example.org"), eq("geheim123"), any(), any());
	}

	@Test
	void registrierungGelingtAuchWennAnmeldungScheitert() throws Exception {
		when(identitaetsanbieter.benutzerAnlegen(eq("ohne-anmeldung@example.org"), any(), any(), any()))
				.thenReturn(UUID.randomUUID());
		doThrow(new IllegalStateException("Keycloak nicht erreichbar"))
				.when(anmeldung).anmelden(eq("ohne-anmeldung@example.org"), any(), any(), any());

		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("ohne-anmeldung@example.org", "[\"PORTFOLIO\"]")))
				.andExpect(status().isCreated());
	}

	@Test
	void ohneCsrfTokenAbgelehnt() throws Exception {
		mockMvc.perform(post("/api/registrierung")
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("ohne-csrf@example.org", "[\"PORTFOLIO\"]")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_UNGUELTIG"));
	}

	@Test
	void ohneKomponenteUngueltig() throws Exception {
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("leer@example.org", "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("komponenten"))
				.andExpect(jsonPath("$.felder[0].code").value("NotEmpty"));
	}

	@Test
	void unbekannteKomponenteUngueltig() throws Exception {
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("unbekannt@example.org", "[\"KALENDER\"]")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void ungueltigeEmailUngueltig() throws Exception {
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("keine-email", "[\"PORTFOLIO\"]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("email"))
				.andExpect(jsonPath("$.felder[0].code").value("Email"));
	}

	@Test
	void zuKurzesPasswortUngueltig() throws Exception {
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "kurz@example.org", "passwort": "kurz", "vorname": "Max", "name": "Muster",
								 "komponenten": ["PORTFOLIO"]}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("passwort"))
				.andExpect(jsonPath("$.felder[0].code").value("Size"))
				.andExpect(jsonPath("$.felder[0].parameter.min").value(8));
	}

	@Test
	void zuLangerNameUngueltig() throws Exception {
		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "lang@example.org", "passwort": "Geheim123!", "vorname": "Max", "name": "%s",
								 "komponenten": ["PORTFOLIO"]}
								""".formatted("x".repeat(256))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.felder[0].feld").value("name"))
				.andExpect(jsonPath("$.felder[0].code").value("Size"))
				.andExpect(jsonPath("$.felder[0].parameter.max").value(255));
		verifyNoInteractions(identitaetsanbieter);
	}

	@Test
	void vergebeneEmailKonflikt() throws Exception {
		when(identitaetsanbieter.benutzerAnlegen(eq("vergeben@example.org"), any(), any(), any()))
				.thenThrow(new EmailBereitsVergebenException("vergeben@example.org"));

		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("vergeben@example.org", "[\"PORTFOLIO\"]")))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("EMAIL_BEREITS_VERGEBEN"))
				.andExpect(jsonPath("$.detail").value("An account already exists for email address vergeben@example.org."));
	}

	@Test
	void abgelehntesPasswortUngueltig() throws Exception {
		when(identitaetsanbieter.benutzerAnlegen(eq("kurz@example.org"), any(), any(), any()))
				.thenThrow(new RegistrierungAbgelehntException("Invalid password: minimum length 12."));

		mockMvc.perform(post("/api/registrierung").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(anfrage("kurz@example.org", "[\"PORTFOLIO\"]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REGISTRIERUNG_ABGELEHNT"))
				.andExpect(jsonPath("$.detail").value("Invalid password: minimum length 12."));
	}

	@Test
	void kontoWirdBeiFehlerBeimSpeichernGeloescht() {
		// Liefert der Identity Provider eine bereits vergebene ID, schlägt das Speichern fehl.
		UUID id = UUID.randomUUID();
		when(identitaetsanbieter.benutzerAnlegen(any(), any(), any(), any())).thenReturn(id);
		var anfrage = new RegistrierungAnfrage("doppelt@example.org", "geheim123", "Max", "Muster",
				Set.of(Komponente.VERTRAGSUEBERSICHT));
		registrierungService.registrieren(anfrage);

		assertThatThrownBy(() -> registrierungService.registrieren(anfrage))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(identitaetsanbieter).benutzerLoeschen(id);
	}

	/** Der ursprüngliche Fehler kommt durch, der beim Löschen hängt daran (das Konto muss von Hand weg). */
	@Test
	void loeschfehlerHaengtAmUrspruenglichenFehler() {
		UUID id = UUID.randomUUID();
		when(identitaetsanbieter.benutzerAnlegen(any(), any(), any(), any())).thenReturn(id);
		var anfrage = new RegistrierungAnfrage("doppelt-ohne-loeschen@example.org", "geheim123", "Max", "Muster",
				Set.of(Komponente.VERTRAGSUEBERSICHT));
		registrierungService.registrieren(anfrage);
		var loeschfehler = new IllegalStateException("Keycloak nicht erreichbar");
		doThrow(loeschfehler).when(identitaetsanbieter).benutzerLoeschen(id);

		assertThatThrownBy(() -> registrierungService.registrieren(anfrage))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(e -> assertThat(e.getSuppressed()).containsExactly(loeschfehler));
	}

	private static String anfrage(String email, String komponenten) {
		return """
				{"email": "%s", "passwort": "geheim123", "vorname": "Max", "name": "Muster",
				 "komponenten": %s}
				""".formatted(email, komponenten);
	}

}
