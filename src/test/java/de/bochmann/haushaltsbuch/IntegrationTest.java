package de.bochmann.haushaltsbuch;

import de.bochmann.haushaltsbuch.nutzer.IdentitaetsanbieterMock;
import de.bochmann.haushaltsbuch.shared.security.Anmeldung;
import de.bochmann.haushaltsbuch.shared.security.KeycloakTokenClientMock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Integrationstest mit der ganzen Anwendung: Profil {@code dev}, PostgreSQL per Testcontainers,
 * MockMvc.
 * <p>
 * Alle Zugriffe auf Keycloak sind Mocks, damit die Tests ohne Keycloak laufen und sich einen
 * Spring-Kontext teilen: Admin API ({@link IdentitaetsanbieterMock}), Token-Endpunkt
 * ({@link KeycloakTokenClientMock}) und der Health-Indikator {@code keycloakHealthIndicator}, der
 * standardmäßig {@code UP} meldet ({@link KeycloakErreichbar}). Tests holen sich die Mocks per
 * {@code @Autowired} und legen ihr Verhalten mit {@code when(...)} fest; Spring setzt sie nach
 * jedem Test zurück. {@link Anmeldung} ist ein Spy: Sie arbeitet echt (gegen den gemockten
 * Token-Endpunkt), lässt sich aber prüfen ({@code verify}) und gezielt scheitern lassen.
 * <p>
 * Eigene {@code @MockitoBean}s, {@code @TestPropertySource} oder weitere Profile in einer
 * Testklasse erzeugen einen eigenen Kontext und verlangsamen den Build; nur in begründeten
 * Ausnahmen (z.&nbsp;B. {@code CsrfCookieTests}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@IdentitaetsanbieterMock
@KeycloakTokenClientMock
@MockitoBean(name = "keycloakHealthIndicator", types = HealthIndicator.class)
@MockitoSpyBean(types = Anmeldung.class)
@ExtendWith(KeycloakErreichbar.class)
public @interface IntegrationTest {

}
