package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "keycloak"})
class KomponentenControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HaushaltService haushaltService;

	@Test
	void liefertGebuchteKomponentenSortiert() throws Exception {
		UUID id = UUID.randomUUID();
		haushaltService.anlegen(id, Set.of(Komponente.PORTFOLIO, Komponente.FINANZUEBERSICHT));

		mockMvc.perform(get("/api/nutzer/ich/komponenten").with(jwt().jwt(token -> token.subject(id.toString()))))
				.andExpect(status().isOk())
				.andExpect(content().json("[\"FINANZUEBERSICHT\", \"PORTFOLIO\"]", true));
	}

	@Test
	void liefertKomponentenAuchBeiSessionLogin() throws Exception {
		UUID id = UUID.randomUUID();
		haushaltService.anlegen(id, Set.of(Komponente.VERTRAGSUEBERSICHT));

		mockMvc.perform(get("/api/nutzer/ich/komponenten")
						.with(oidcLogin().idToken(token -> token.subject(id.toString()))))
				.andExpect(status().isOk())
				.andExpect(content().json("[\"VERTRAGSUEBERSICHT\"]", true));
	}

	@Test
	void unbekannterNutzerHatKeineKomponenten() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich/komponenten")
						.with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	@Test
	void ohneAnmeldungNichtErlaubt() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich/komponenten")).andExpect(status().isUnauthorized());
	}

}
