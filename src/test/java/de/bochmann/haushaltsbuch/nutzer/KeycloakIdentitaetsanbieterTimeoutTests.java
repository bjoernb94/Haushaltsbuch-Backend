package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.LangsamerServer;
import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Antwortet Keycloak nicht, bricht die Admin API nach dem Timeout ab, auch beim Token des Service
 * Accounts (#29). Gebaut wie in der Anwendung über {@link KeycloakAdminConfig}.
 */
class KeycloakIdentitaetsanbieterTimeoutTests {

	private static final String TOKEN = "/realms/haushaltsbuch/protocol/openid-connect/token";

	private static final String ADMIN = "/admin/realms/haushaltsbuch";

	/** Deutlich unter {@link LangsamerServer#VERZOEGERUNG}: Der Client darf nicht auf die Antwort warten. */
	private static final Duration HOECHSTENS = Duration.ofSeconds(2);

	private final LangsamerServer keycloak = new LangsamerServer();

	KeycloakIdentitaetsanbieterTimeoutTests() throws IOException {
	}

	@AfterEach
	void schliessen() {
		keycloak.close();
	}

	@Test
	void tokenDesServiceAccountsBrichtAb() {
		keycloak.haengt(TOKEN);

		brichtAb(() -> identitaetsanbieter().benutzerAnlegen("max@example.org", "Geheim123!", "Max", "Muster"));
	}

	@Test
	void anlegenBrichtAb() {
		keycloak.antwortet(TOKEN, """
				{"access_token": "at", "token_type": "Bearer", "expires_in": 300}
				""")
				.haengt(ADMIN + "/users");

		brichtAb(() -> identitaetsanbieter().benutzerAnlegen("max@example.org", "Geheim123!", "Max", "Muster"));
	}

	@Test
	void loeschenBrichtAb() {
		keycloak.antwortet(TOKEN, """
				{"access_token": "at", "token_type": "Bearer", "expires_in": 300}
				""")
				.haengt(ADMIN + "/users");

		brichtAb(() -> identitaetsanbieter().benutzerLoeschen(UUID.randomUUID()));
	}

	private Identitaetsanbieter identitaetsanbieter() {
		var registrierung = ClientRegistration.withRegistrationId(KeycloakAdminConfig.CLIENT_REGISTRATION_ID)
				.clientId("haushaltsbuch-backend")
				.clientSecret("geheim")
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri(keycloak.uri() + TOKEN)
				.build();
		var registrierungen = new InMemoryClientRegistrationRepository(registrierung);
		return new KeycloakAdminConfig().keycloakIdentitaetsanbieter(registrierungen,
				new InMemoryOAuth2AuthorizedClientService(registrierungen), keycloak.uri() + ADMIN,
				LangsamerServer.requestFactory());
	}

	private static void brichtAb(Executable aufruf) {
		assertTimeoutPreemptively(HOECHSTENS, () -> assertThatThrownBy(aufruf::execute)
				.isInstanceOf(KeycloakNichtErreichbarException.class));
	}

}
