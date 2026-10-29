package de.bochmann.haushaltsbuch.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Grundgerüst der Security-Konfiguration.
 * <p>
 * Mit Profil {@code keycloak} (in {@code prod} immer aktiv) laufen zwei Wege zur
 * Authentifizierung über dieselbe Filterkette:
 * <ul>
 *   <li><b>BFF</b> ({@code oauth2Login}): Das Backend führt den Login-Flow gegen Keycloak
 *   serverseitig aus, das Frontend erhält nur ein Session-Cookie.</li>
 *   <li><b>Resource Server</b> ({@code oauth2ResourceServer}): Requests mit Bearer-Token
 *   (z.&nbsp;B. Tests, andere Clients) werden gegen den Keycloak-Issuer geprüft.</li>
 * </ul>
 * Ohne Profil {@code keycloak} ist die Anwendung für die lokale Entwicklung offen.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private static final String[] OPENAPI_PATHS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

	@Bean
	@Profile("keycloak")
	SecurityFilterChain keycloakSecurityFilterChain(HttpSecurity http,
			@Value("${haushaltsbuch.security.openapi-public:false}") boolean openApiPublic) throws Exception {
		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
			if (openApiPublic) {
				auth.requestMatchers(OPENAPI_PATHS).permitAll();
			}
			// TODO Keycloak: Rollenbasierte Regeln ergänzen, z. B.
			// auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
			auth.requestMatchers("/api/**").authenticated();
			auth.anyRequest().authenticated();
		});

		// BFF: Login-Flow serverseitig. Die Client-Registrierung "keycloak"
		// (Realm, Client-ID, Client-Secret) wird in application.yml konfiguriert.
		http.oauth2Login(withDefaults());

		// TODO Keycloak: Rollen-Mapping für Bearer-Tokens ergänzen. Keycloak liefert
		// Realm-Rollen unter "realm_access.roles" und Client-Rollen unter
		// "resource_access.<client-id>.roles"; dafür einen JwtAuthenticationConverter
		// mit eigenem GrantedAuthoritiesConverter registrieren:
		// http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(...)));
		// Für oauth2Login analog einen GrantedAuthoritiesMapper / OidcUserService bereitstellen.
		http.oauth2ResourceServer(rs -> rs.jwt(withDefaults()));

		// API-Aufrufe ohne Anmeldung erhalten 401 statt eines Redirects auf die Login-Seite.
		http.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
				PathPatternRequestMatcher.withDefaults().matcher("/api/**")));

		// Session-Cookie im BFF-Muster erfordert CSRF-Schutz; das Token wird als Cookie
		// (XSRF-TOKEN) an die SPA ausgeliefert und als Header X-XSRF-TOKEN zurückerwartet.
		http.csrf(csrf -> csrf.spa());

		return http.build();
	}

	/**
	 * Offene Filterkette für die lokale Entwicklung ohne Keycloak. Kann in {@code prod}
	 * nicht greifen, da dort das Profil {@code keycloak} über eine Profilgruppe aktiv ist.
	 */
	@Bean
	@Profile("!keycloak")
	SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
		log.warn("Keycloak-Profil nicht aktiv: alle Endpunkte sind ohne Anmeldung erreichbar.");
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		http.csrf(csrf -> csrf.spa());
		return http.build();
	}

}
