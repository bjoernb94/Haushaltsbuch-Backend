package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ProfilControllerTests {

	private static final String PROFIL = """
			{"vorname": "Max", "name": "Muster", "email": "max@example.org"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void liefertProfilAusDerAnmeldung() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich").with(oidcLogin().idToken(token -> token
						.subject(UUID.randomUUID().toString())
						.claim("given_name", "Max")
						.claim("family_name", "Muster")
						.claim("email", "max@example.org"))))
				.andExpect(status().isOk())
				.andExpect(content().json(PROFIL, true));
	}

	@Test
	void fehlendeAngabeIstEinFehlerStattNull() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich").with(oidcLogin().idToken(token -> token
						.subject(UUID.randomUUID().toString())
						.claim("family_name", "Muster")
						.claim("email", "max@example.org"))))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNER_FEHLER"));
	}

	@Test
	void ohneAnmeldungNichtErlaubt() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich")).andExpect(status().isUnauthorized());
	}

}
