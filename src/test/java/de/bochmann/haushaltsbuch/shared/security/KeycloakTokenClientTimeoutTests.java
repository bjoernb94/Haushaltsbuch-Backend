package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.LangsamerServer;
import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Antwortet Keycloak nicht, bricht der Token-Client nach dem Timeout ab, auch beim Laden der
 * Schlüssel des Realms (#29). Gebaut wie in der Anwendung über {@link AnmeldungConfig}.
 */
class KeycloakTokenClientTimeoutTests {

	private static final String REALM = "/realms/haushaltsbuch";

	private static final String OIDC = REALM + "/protocol/openid-connect";

	/** Deutlich unter {@link LangsamerServer#VERZOEGERUNG}: Der Client darf nicht auf die Antwort warten. */
	private static final Duration HOECHSTENS = Duration.ofSeconds(2);

	private final LangsamerServer keycloak = new LangsamerServer();

	KeycloakTokenClientTimeoutTests() throws IOException {
	}

	@AfterEach
	void schliessen() {
		keycloak.close();
	}

	@Test
	void anmeldungBrichtAb() {
		keycloak.haengt(OIDC + "/token");

		brichtAb(() -> tokenClient().anmelden("max@example.org", "Geheim123!"));
	}

	/** Das Token kommt sofort, aber die Schlüssel zum Prüfen des ID-Tokens nicht. */
	@Test
	void anmeldungBrichtBeimLadenDerSchluesselAb() {
		keycloak.antwortet(OIDC + "/token", """
				{"access_token": "at", "expires_in": 300, "refresh_token": "rt", "id_token": "%s"}
				""".formatted(idToken()))
				.haengt(OIDC + "/certs");

		brichtAb(() -> tokenClient().anmelden("max@example.org", "Geheim123!"));
	}

	@Test
	void erneuernBrichtAb() {
		keycloak.haengt(OIDC + "/token");

		brichtAb(() -> tokenClient().erneuern("rt"));
	}

	@Test
	void abmeldenBrichtAb() {
		keycloak.haengt(OIDC + "/logout");

		brichtAb(() -> tokenClient().abmelden("rt"));
	}

	private KeycloakTokenClient tokenClient() {
		return new AnmeldungConfig().keycloakTokenClient(keycloak.uri() + REALM, "haushaltsbuch-backend", "geheim",
				LangsamerServer.requestFactory());
	}

	private static void brichtAb(Executable aufruf) {
		assertTimeoutPreemptively(HOECHSTENS, () -> assertThatThrownBy(aufruf::execute)
				.isInstanceOf(KeycloakNichtErreichbarException.class));
	}

	/** Lesbares, aber nicht gültig signiertes JWT: Zum Prüfen lädt der Decoder erst die Schlüssel. */
	private static String idToken() {
		var base64 = Base64.getUrlEncoder().withoutPadding();
		return base64.encodeToString("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ base64.encodeToString("{\"sub\":\"max\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ base64.encodeToString("signatur".getBytes(StandardCharsets.UTF_8));
	}

}
