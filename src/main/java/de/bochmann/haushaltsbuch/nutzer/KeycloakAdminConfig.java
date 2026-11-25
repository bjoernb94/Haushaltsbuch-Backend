package de.bochmann.haushaltsbuch.nutzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Zugriff auf die Keycloak Admin REST API über die Client-Registrierung
 * {@value #CLIENT_REGISTRATION_ID} (Client Credentials, siehe application.yml).
 * <p>
 * Der Client braucht in Keycloak einen Service Account mit der Rolle
 * {@code realm-management/manage-users} und den optionalen Client-Scope {@code kc-admin-api}, den die
 * Registrierung anfordert: Ab Keycloak 26.8.2 lehnt die Admin API Tokens ohne ihn mit 401 ab.
 */
@Configuration(proxyBeanMethods = false)
class KeycloakAdminConfig {

	static final String CLIENT_REGISTRATION_ID = "keycloak-admin";

	/** Das Token gehört dem Service Account, nicht dem gerade (anonym) anfragenden Nutzer. */
	private static final Authentication SERVICE_ACCOUNT =
			UsernamePasswordAuthenticationToken.unauthenticated(CLIENT_REGISTRATION_ID, null);

	/**
	 * @param requestFactory von Boot, mit den Timeouts aus {@code spring.http.clients.*}; gilt auch für
	 *        das Token des Service Accounts
	 */
	@Bean
	Identitaetsanbieter keycloakIdentitaetsanbieter(ClientRegistrationRepository clientRegistrations,
			OAuth2AuthorizedClientService authorizedClients,
			@Value("${haushaltsbuch.keycloak.admin-uri}") String adminUri, ClientHttpRequestFactory requestFactory) {
		// Bewusst kein eigenes OAuth2AuthorizedClientManager-Bean, um das von Spring Security
		// registrierte nicht zu ersetzen.
		var clientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations,
				authorizedClients);
		clientManager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials(c -> c.accessTokenResponseClient(tokenClient(requestFactory)))
				.build());
		var oauth2 = new OAuth2ClientHttpRequestInterceptor(clientManager);
		oauth2.setClientRegistrationIdResolver(request -> CLIENT_REGISTRATION_ID);
		oauth2.setPrincipalResolver(request -> SERVICE_ACCOUNT);

		RestClient restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.baseUrl(adminUri)
				.requestInterceptor(oauth2)
				.build();
		return new KeycloakIdentitaetsanbieter(restClient);
	}

	/**
	 * Token-Client für Client Credentials wie der Standard von Spring Security (gleiche Konverter und
	 * Fehlerbehandlung), nur mit unserer Request-Factory, damit auch hier die Timeouts gelten.
	 */
	static RestClientClientCredentialsTokenResponseClient tokenClient(ClientHttpRequestFactory requestFactory) {
		var tokenClient = new RestClientClientCredentialsTokenResponseClient();
		tokenClient.setRestClient(RestClient.builder()
				.requestFactory(requestFactory)
				.configureMessageConverters(konverter -> {
					konverter.addCustomConverter(new FormHttpMessageConverter());
					konverter.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
				})
				.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
				.build());
		return tokenClient;
	}

}
