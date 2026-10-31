package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eigener, frischer Kontext: {@code csrf()} aus spring-security-test ersetzt das CSRF-Repository
 * im geteilten Kontext dauerhaft, danach würde hier kein echtes Cookie mehr gesetzt.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void xsrfCookieSchonBeimErstenGet() throws Exception {
		// Die SPA braucht das Token vor ihrem ersten POST (Anmeldung, Registrierung).
		mockMvc.perform(get("/actuator/health"))
				.andExpect(cookie().exists("XSRF-TOKEN"));
		// Auch die 401 beim Prüfen, ob der Nutzer angemeldet ist, liefert das Cookie.
		mockMvc.perform(get("/api/nutzer/ich/komponenten"))
				.andExpect(status().isUnauthorized())
				.andExpect(cookie().exists("XSRF-TOKEN"));
	}

}
