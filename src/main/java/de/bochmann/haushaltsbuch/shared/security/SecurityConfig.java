package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.Komponente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Grundgerüst der Security-Konfiguration.
 * <p>
 * Mit Profil {@code keycloak} (in {@code prod} immer aktiv) laufen zwei Wege zur
 * Authentifizierung über dieselbe Filterkette:
 * <ul>
 *   <li><b>Session</b>: Die SPA schickt E-Mail und Passwort an {@code POST /api/anmeldung}, das
 *   Backend holt die Tokens direkt bei Keycloak ({@link Anmeldung}), das Frontend erhält nur ein
 *   Session-Cookie. Es gibt keine Weiterleitung zu Keycloak.</li>
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
	SecurityFilterChain keycloakSecurityFilterChain(HttpSecurity http, KomponentenAbfrage komponentenAbfrage,
			KeycloakTokenClient tokenClient, CsrfTokenRepository csrfTokenRepository,
			@Value("${haushaltsbuch.security.openapi-public:false}") boolean openApiPublic) throws Exception {
		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
			if (openApiPublic) {
				auth.requestMatchers(OPENAPI_PATHS).permitAll();
			}
			// Eigenes Registrierungsformular der SPA; legt das Konto über die Keycloak Admin API an.
			auth.requestMatchers(HttpMethod.POST, "/api/registrierung", "/api/anmeldung").permitAll();
			// Fachbereiche nur mit gebuchter Komponente, sonst 403.
			auth.requestMatchers("/api/buchungen/**").access(gebucht(Komponente.FINANZUEBERSICHT, komponentenAbfrage));
			auth.requestMatchers("/api/vertraege/**").access(gebucht(Komponente.VERTRAGSUEBERSICHT, komponentenAbfrage));
			auth.requestMatchers("/api/versorgung/**").access(gebucht(Komponente.VERSORGUNGSVERTRAEGE, komponentenAbfrage));
			auth.requestMatchers("/api/portfolio/**").access(gebucht(Komponente.PORTFOLIO, komponentenAbfrage));
			auth.requestMatchers("/api/**").authenticated();
			auth.anyRequest().authenticated();
		});

		// Prüft bei Session-Anmeldungen regelmäßig, ob die Sitzung bei Keycloak noch besteht.
		http.addFilterBefore(new SitzungErneuernFilter(tokenClient), SecurityContextHolderFilter.class);

		// POST /api/abmeldung beendet die Session im Backend und die Sitzung bei Keycloak.
		http.logout(logout -> logout
				.logoutUrl("/api/abmeldung")
				.addLogoutHandler(new KeycloakLogoutHandler(tokenClient))
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

		// Keycloak-Rollen werden bewusst nicht ausgewertet; der Zugriff auf Fachbereiche hängt an den
		// gebuchten Komponenten. Werden später Rollen gebraucht (z. B. Admin-Bereich): Keycloak liefert
		// sie unter "realm_access.roles" bzw. "resource_access.<client-id>.roles". Dann einen
		// JwtAuthenticationConverter mit eigenem GrantedAuthoritiesConverter registrieren
		// (rs.jwt(jwt -> jwt.jwtAuthenticationConverter(...))) und in Anmeldung die Authorities
		// des DefaultOidcUser aus dem Access Token befüllen.
		http.oauth2ResourceServer(rs -> rs.jwt(withDefaults()));

		// Ohne Anmeldung 401; eine Login-Seite im Backend gibt es nicht.
		http.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

		// Session-Cookie im BFF-Muster erfordert CSRF-Schutz; das Token wird als Cookie
		// (XSRF-TOKEN) an die SPA ausgeliefert und als Header X-XSRF-TOKEN zurückerwartet.
		// Eigenes Repository, damit die Anmeldung das Token erneuern kann.
		http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository));

		return http.build();
	}

	/**
	 * Erlaubt den Zugriff nur, wenn der angemeldete Nutzer die Komponente gebucht hat. Die
	 * Komponenten werden bei jeder Anfrage gelesen, damit Änderungen sofort greifen. Anonyme
	 * Anfragen werden abgelehnt und erhalten über den Entry Point 401.
	 */
	private static AuthorizationManager<RequestAuthorizationContext> gebucht(Komponente komponente,
			KomponentenAbfrage komponentenAbfrage) {
		return (authentication, kontext) -> new AuthorizationDecision(AngemeldeterNutzer.id(authentication.get())
				.map(id -> komponentenAbfrage.komponentenVon(id).contains(komponente))
				.orElse(false));
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
