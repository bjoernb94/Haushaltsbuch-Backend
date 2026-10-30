package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.security.AnmeldungFehlgeschlagenException.Grund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakTokenClientTests {

	private static final String OIDC_URI = "http://keycloak.test/realms/haushaltsbuch/protocol/openid-connect";

	private MockRestServiceServer server;

	private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);

	private KeycloakTokenClient tokenClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(OIDC_URI);
		server = MockRestServiceServer.bindTo(builder).build();
		tokenClient = new KeycloakTokenClient(builder.build(), jwtDecoder, "haushaltsbuch-backend", "geheim");
	}

	@Test
	void meldetMitPasswortAn() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(Map.of(
						"grant_type", "password",
						"client_id", "haushaltsbuch-backend",
						"client_secret", "geheim",
						"username", "max@example.org",
						"password", "Geheim123!",
						"scope", "openid")))
				.andRespond(withSuccess("""
						{"access_token": "at", "expires_in": 300, "refresh_token": "rt", "id_token": "it"}
						""", MediaType.APPLICATION_JSON));
		when(jwtDecoder.decode("it")).thenReturn(Jwt.withTokenValue("it").header("alg", "RS256")
				.subject("4f9d3c2e-6a1b-4c8d-9e0f-123456789abc")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

		var anmeldedaten = tokenClient.anmelden("max@example.org", "Geheim123!");

		assertThat(anmeldedaten.idToken().getSubject()).isEqualTo("4f9d3c2e-6a1b-4c8d-9e0f-123456789abc");
		assertThat(anmeldedaten.sitzung().refreshToken()).isEqualTo("rt");
		assertThat(anmeldedaten.sitzung().accessTokenAblauf()).isAfter(Instant.now().plusSeconds(290));
		server.verify();
	}

	@Test
	void falschesPasswortUngueltig() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andRespond(withStatus(UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"invalid_grant\", \"error_description\": \"Invalid user credentials\"}"));

		assertThatThrownBy(() -> tokenClient.anmelden("max@example.org", "falsch"))
				.isInstanceOfSatisfying(AnmeldungFehlgeschlagenException.class,
						e -> assertThat(e.getGrund()).isEqualTo(Grund.UNGUELTIG));
	}

	@Test
	void deaktiviertesKontoGesperrt() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andRespond(withStatus(BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"invalid_grant\", \"error_description\": \"Account disabled\"}"));

		assertThatThrownBy(() -> tokenClient.anmelden("max@example.org", "Geheim123!"))
				.isInstanceOfSatisfying(AnmeldungFehlgeschlagenException.class,
						e -> assertThat(e.getGrund()).isEqualTo(Grund.GESPERRT));
	}

	@Test
	void offenePflichtschritteNichtEingerichtet() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andRespond(withStatus(BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"invalid_grant\", \"error_description\": \"Account is not fully set up\"}"));

		assertThatThrownBy(() -> tokenClient.anmelden("max@example.org", "Geheim123!"))
				.isInstanceOfSatisfying(AnmeldungFehlgeschlagenException.class,
						e -> assertThat(e.getGrund()).isEqualTo(Grund.NICHT_EINGERICHTET));
	}

	@Test
	void abgeschalteterPasswortGrantIstKonfigurationsfehler() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andRespond(withStatus(BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"unauthorized_client\","
								+ " \"error_description\": \"Client not allowed for direct access grants\"}"));

		assertThatThrownBy(() -> tokenClient.anmelden("max@example.org", "Geheim123!"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("unauthorized_client");
	}

	@Test
	void erneuertSitzung() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andExpect(content().formDataContains(Map.of(
						"grant_type", "refresh_token",
						"refresh_token", "rt-alt")))
				.andRespond(withSuccess("""
						{"access_token": "at", "expires_in": 300, "refresh_token": "rt-neu"}
						""", MediaType.APPLICATION_JSON));

		assertThat(tokenClient.erneuern("rt-alt").refreshToken()).isEqualTo("rt-neu");
		server.verify();
	}

	@Test
	void beendeteSitzungNichtErneuerbar() {
		server.expect(requestTo(OIDC_URI + "/token"))
				.andRespond(withStatus(BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
						.body("{\"error\": \"invalid_grant\", \"error_description\": \"Session not active\"}"));

		assertThatThrownBy(() -> tokenClient.erneuern("rt"))
				.isInstanceOf(AnmeldungFehlgeschlagenException.class);
	}

	@Test
	void meldetBeiKeycloakAb() {
		server.expect(requestTo(OIDC_URI + "/logout"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().formDataContains(Map.of(
						"client_id", "haushaltsbuch-backend",
						"client_secret", "geheim",
						"refresh_token", "rt")))
				.andRespond(withNoContent());

		tokenClient.abmelden("rt");
		server.verify();
	}

}
