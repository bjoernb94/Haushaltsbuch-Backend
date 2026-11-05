package de.bochmann.haushaltsbuch.shared.systemstatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Health-Indikator {@code keycloak} für die Health-Gruppe {@code systemstatus} (siehe
 * {@code application.yml}) und die Stage in {@code /actuator/info}.
 */
@Configuration(proxyBeanMethods = false)
class SystemstatusConfig {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Bean
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

	/**
	 * Als Bean statt über {@code info.*}, weil der Wert so schon beim Start gelesen wird: Ohne gültige
	 * Stage startet die Instanz nicht, statt ohne Warnstreifen in der SPA zu laufen.
	 */
	@Bean
	InfoContributor stageInfoContributor(@Value("${haushaltsbuch.stage}") Stage stage) {
		return info -> info.withDetail("app", Map.of("stage", stage));
	}

}
