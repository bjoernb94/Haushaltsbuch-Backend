package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "keycloak"})
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void openApiIsPublicInDev() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/buchungen")).andExpect(status().isUnauthorized());
	}

}
