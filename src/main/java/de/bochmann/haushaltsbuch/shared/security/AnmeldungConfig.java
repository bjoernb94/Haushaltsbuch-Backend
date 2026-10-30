package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Anmeldung mit E-Mail und Passwort über das Backend (siehe {@link Anmeldung}).
 */
@Configuration(proxyBeanMethods = false)
@Profile("keycloak")
class AnmeldungConfig {

	/** Wird auch in der Filterkette verwendet, damit die Anmeldung das CSRF-Token erneuern kann. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	KeycloakTokenClient keycloakTokenClient(JwtDecoder jwtDecoder,
			@Value("${haushaltsbuch.keycloak.issuer-uri}") String issuerUri,
			@Value("${haushaltsbuch.keycloak.client-id}") String clientId,
			@Value("${haushaltsbuch.keycloak.client-secret}") String clientSecret) {
		RestClient restClient = RestClient.builder()
				.baseUrl(issuerUri + "/protocol/openid-connect")
				.build();
		return new KeycloakTokenClient(restClient, jwtDecoder, clientId, clientSecret);
	}

	@Bean
	Anmeldung anmeldung(KeycloakTokenClient tokenClient, CsrfTokenRepository csrfTokenRepository) {
		return new Anmeldung(tokenClient, new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository))));
	}

}
