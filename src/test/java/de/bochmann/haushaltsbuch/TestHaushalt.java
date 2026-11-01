package de.bochmann.haushaltsbuch;

import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

/**
 * Ein Haushalt mit einem Mitglied für Tests der Fachmodule, z.&nbsp;B. für den Pflicht-Test „Daten
 * eines fremden Haushalts → 404“:
 * <pre>
 * var eigener = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
 * var fremder = TestHaushalt.anlegen(haushaltService, Komponente.VERTRAGSUEBERSICHT);
 * // als eigener anlegen, als fremder abrufen, ändern, löschen → 404
 * mockMvc.perform(get("/api/vertraege/{id}", id).with(fremder.anmeldung())).andExpect(status().isNotFound());
 * </pre>
 */
public record TestHaushalt(UUID nutzerId, UUID haushaltId) {

	public static TestHaushalt anlegen(HaushaltService haushaltService, Komponente... komponenten) {
		UUID nutzerId = UUID.randomUUID();
		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(komponenten));
		return new TestHaushalt(nutzerId, haushaltId);
	}

	/** Anfrage als Mitglied dieses Haushalts (Session-Anmeldung mit {@code sub}). */
	public RequestPostProcessor anmeldung() {
		return oidcLogin().idToken(token -> token.subject(nutzerId.toString()));
	}

}
