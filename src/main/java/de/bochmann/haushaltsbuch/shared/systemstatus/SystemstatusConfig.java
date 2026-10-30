package de.bochmann.haushaltsbuch.shared.systemstatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Health-Indikator {@code keycloak} für die Health-Gruppe {@code systemstatus} (siehe
 * {@code application.yml}). Den gibt es in jedem Profil, weil die Gruppe sonst beim Start
 * abgelehnt würde.
 */
@Configuration(proxyBeanMethods = false)
class SystemstatusConfig {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Bean
	@Profile("keycloak")
	HealthIndicator keycloakHealthIndicator(@Value("${haushaltsbuch.keycloak.issuer-uri}") String issuerUri) {
		var requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(TIMEOUT);
		requestFactory.setReadTimeout(TIMEOUT);
		RestClient restClient = RestClient.builder()
				.baseUrl(issuerUri)
				.requestFactory(requestFactory)
				.build();
		return new KeycloakHealthIndicator(restClient, Clock.systemUTC());
	}

	/** Ohne Profil {@code keycloak} gibt es keine Anmeldung, der Anmeldeserver gilt als nicht erreichbar. */
	@Bean("keycloakHealthIndicator")
	@Profile("!keycloak")
	HealthIndicator keycloakHealthIndicatorOhneKeycloak() {
		return () -> Health.down().withDetail("grund", "Profil keycloak nicht aktiv").build();
	}

}
