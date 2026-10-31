package de.bochmann.haushaltsbuch.nutzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Zugriff auf die Keycloak Admin REST API über die Client-Registrierung
 * {@value #CLIENT_REGISTRATION_ID} (Client Credentials, siehe application.yml).
 * <p>
 * Der Client braucht in Keycloak einen Service Account mit der Rolle
 * {@code realm-management/manage-users}.
 */
@Configuration(proxyBeanMethods = false)
class KeycloakAdminConfig {

	static final String CLIENT_REGISTRATION_ID = "keycloak-admin";

	/** Das Token gehört dem Service Account, nicht dem gerade (anonym) anfragenden Nutzer. */
	private static final Authentication SERVICE_ACCOUNT =
			UsernamePasswordAuthenticationToken.unauthenticated(CLIENT_REGISTRATION_ID, null);

	@Bean
	Identitaetsanbieter keycloakIdentitaetsanbieter(ClientRegistrationRepository clientRegistrations,
			OAuth2AuthorizedClientService authorizedClients,
			@Value("${haushaltsbuch.keycloak.admin-uri}") String adminUri) {
		// Bewusst kein eigenes OAuth2AuthorizedClientManager-Bean, um das von Spring Security
		// registrierte nicht zu ersetzen.
		var clientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations,
				authorizedClients);
		var oauth2 = new OAuth2ClientHttpRequestInterceptor(clientManager);
		oauth2.setClientRegistrationIdResolver(request -> CLIENT_REGISTRATION_ID);
		oauth2.setPrincipalResolver(request -> SERVICE_ACCOUNT);

		RestClient restClient = RestClient.builder()
				.baseUrl(adminUri)
				.requestInterceptor(oauth2)
				.build();
		return new KeycloakIdentitaetsanbieter(restClient);
	}

}
