package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProfilControllerTests {

	private static final String PROFIL = """
			{"vorname": "Max", "name": "Muster", "email": "max@example.org"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void liefertProfilBeiSessionAnmeldung() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich").with(oidcLogin().idToken(token -> token
						.subject(UUID.randomUUID().toString())
						.claim("given_name", "Max")
						.claim("family_name", "Muster")
						.claim("email", "max@example.org"))))
				.andExpect(status().isOk())
				.andExpect(content().json(PROFIL, true));
	}

	@Test
	void liefertProfilBeiBearerToken() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich").with(jwt().jwt(token -> token
						.subject(UUID.randomUUID().toString())
						.claim("given_name", "Max")
						.claim("family_name", "Muster")
						.claim("email", "max@example.org"))))
				.andExpect(status().isOk())
				.andExpect(content().json(PROFIL, true));
	}

	@Test
	void ohneAnmeldungNichtErlaubt() throws Exception {
		mockMvc.perform(get("/api/nutzer/ich")).andExpect(status().isUnauthorized());
	}

}
