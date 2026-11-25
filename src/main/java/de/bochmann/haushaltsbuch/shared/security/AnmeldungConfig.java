package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Anmeldung mit E-Mail und Passwort über das Backend (siehe {@link Anmeldung}).
 */
@Configuration(proxyBeanMethods = false)
class AnmeldungConfig {

	/** Wird auch in der Filterkette verwendet, damit die Anmeldung das CSRF-Token erneuern kann. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	/**
	 * @param requestFactory von Boot, mit den Timeouts aus {@code spring.http.clients.*}; gilt auch für
	 *        das Laden der Schlüssel des Realms
	 */
	@Bean
	KeycloakTokenClient keycloakTokenClient(@Value("${haushaltsbuch.keycloak.issuer-uri}") String issuerUri,
			@Value("${haushaltsbuch.keycloak.client-id}") String clientId,
			@Value("${haushaltsbuch.keycloak.client-secret}") String clientSecret,
			ClientHttpRequestFactory requestFactory) {
		RestClient restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.baseUrl(issuerUri + "/protocol/openid-connect")
				.build();
		// Prüft das ID-Token: Signatur mit den Schlüsseln des Realms (beim ersten Gebrauch geladen,
		// der Start braucht Keycloak nicht), Ablauf, Issuer und Audience (unser Client).
		var idTokenDecoder = NimbusJwtDecoder.withJwkSetUri(issuerUri + "/protocol/openid-connect/certs")
				.restOperations(new RestTemplate(requestFactory))
				.build();
		idTokenDecoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
				new JwtIssuerValidator(issuerUri), new JwtAudienceValidator(clientId)));
		return new KeycloakTokenClient(restClient, idTokenDecoder, clientId, clientSecret);
	}

	@Bean
	Anmeldung anmeldung(KeycloakTokenClient tokenClient, CsrfTokenRepository csrfTokenRepository) {
		return new Anmeldung(tokenClient, new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository))));
	}

}
