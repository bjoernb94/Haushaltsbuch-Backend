package de.bochmann.haushaltsbuch.shared.systemstatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakHealthIndicatorTests {

	private static final String ISSUER_URI = "http://keycloak.test/realms/haushaltsbuch";

	private static final String DISCOVERY_URI = ISSUER_URI + "/.well-known/openid-configuration";

	private static final Instant JETZT = Instant.parse("2026-10-30T12:00:00Z");

	private MockRestServiceServer server;

	private final Clock clock = mock(Clock.class);

	private KeycloakHealthIndicator indicator;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(ISSUER_URI);
		server = MockRestServiceServer.bindTo(builder).build();
		indicator = new KeycloakHealthIndicator(builder.build(), clock);
		when(clock.instant()).thenReturn(JETZT);
	}

	@Test
	void upWennRealmErreichbar() {
		server.expect(requestTo(DISCOVERY_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void downBeiFehlerantwort() {
		server.expect(requestTo(DISCOVERY_URI)).andRespond(withServerError());

		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	void downWennNichtErreichbar() {
		server.expect(requestTo(DISCOVERY_URI)).andRespond(withException(new IOException("Connection refused")));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	void merktSichDasErgebnisKurz() {
		server.expect(once(), requestTo(DISCOVERY_URI)).andRespond(withServerError());
		server.expect(once(), requestTo(DISCOVERY_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);

		when(clock.instant()).thenReturn(JETZT.plus(KeycloakHealthIndicator.GUELTIGKEIT).minusSeconds(1));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);

		when(clock.instant()).thenReturn(JETZT.plus(KeycloakHealthIndicator.GUELTIGKEIT));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
		server.verify();
	}

}
