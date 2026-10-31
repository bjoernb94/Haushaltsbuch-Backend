package de.bochmann.haushaltsbuch.shared.fehler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zentrale Fehlerbehandlung ohne Spring-Kontext, mit einem Controller nur für diesen Test. Die
 * Anbindung an die echten Endpunkte und die Security prüfen die jeweiligen Controller-Tests.
 */
class FehlerBehandlungTests {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new FehlerBehandlung())
			.build();

	@Test
	void nichtGefunden() throws Exception {
		mockMvc.perform(get("/test/nicht-gefunden"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NICHT_GEFUNDEN"))
				.andExpect(jsonPath("$.detail").value("Buchung 42 not found."));
	}

	@Test
	void unerwarteterFehlerOhneInterna() throws Exception {
		mockMvc.perform(get("/test/unerwartet"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNER_FEHLER"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
	}

	@Test
	void ungueltigeFelderMitCodeUndParametern() throws Exception {
		mockMvc.perform(post("/test/validierung")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"keine-email\", \"passwort\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDIERUNG"))
				.andExpect(jsonPath("$.detail").value("Request contains invalid fields."))
				.andExpect(jsonPath("$.felder.length()").value(3))
				.andExpect(jsonPath("$.felder[?(@.feld == 'email')].code").value("Email"))
				// Ein leeres Passwort verletzt beide Constraints.
				.andExpect(jsonPath("$.felder[?(@.feld == 'passwort')].code")
						.value(containsInAnyOrder("NotBlank", "Size")))
				// Nur gesetzte Attribute, nicht max, message, groups …
				.andExpect(jsonPath("$.felder[?(@.code == 'Size')].parameter").value(
						containsInAnyOrder(Map.of("min", 8))))
				.andExpect(jsonPath("$.felder[?(@.code == 'Email')].parameter").value(
						containsInAnyOrder(Map.of())));
	}

	@Test
	void unlesbarerBody() throws Exception {
		mockMvc.perform(post("/test/validierung")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{kaputt"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ANFRAGE_UNGUELTIG"));
	}

	@Test
	void falscheMethode() throws Exception {
		mockMvc.perform(post("/test/nicht-gefunden"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHODE_NICHT_ERLAUBT"));
	}

	@Test
	void veralteteVersion() throws Exception {
		mockMvc.perform(get("/test/konflikt"))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("KONFLIKT"));
	}

	@Test
	void gleichzeitigeAenderung() throws Exception {
		mockMvc.perform(get("/test/gleichzeitig"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("KONFLIKT"));
	}

	@RestController
	static class TestController {

		record Anfrage(@NotBlank @Email String email, @NotBlank @Size(min = 8) String passwort) {
		}

		@GetMapping("/test/nicht-gefunden")
		void nichtGefunden() {
			throw new NichtGefundenException("Buchung 42 not found.");
		}

		@GetMapping("/test/konflikt")
		void konflikt() {
			throw new KonfliktException("Vertrag 42 was modified in the meantime.");
		}

		@GetMapping("/test/gleichzeitig")
		void gleichzeitig() {
			throw new ObjectOptimisticLockingFailureException(Object.class, 42);
		}

		@GetMapping("/test/unerwartet")
		void unerwartet() {
			throw new IllegalStateException("Interna, die nicht in die Antwort gehören");
		}

		@PostMapping("/test/validierung")
		void validierung(@Valid @RequestBody Anfrage anfrage) {
		}

	}

}
