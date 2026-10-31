package de.bochmann.haushaltsbuch;

import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test der Fachlogik eines Moduls (Services, Repositories): startet nur das Modul der Testklasse
 * ({@link ApplicationModuleTest}), mit Profil {@code dev} und PostgreSQL per Testcontainers.
 * Greift das Modul auf Beans eines anderen Moduls zu, schlägt der Start fehl; solche Beans mit
 * {@code @MockitoBean} ersetzen. Jedes Modul bekommt einen eigenen Spring-Kontext.
 * <p>
 * Web- und Security-Tests (MockMvc, Filterkette) laufen dagegen mit {@link IntegrationTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ApplicationModuleTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
// Der Health-Indikator keycloak der Gruppe systemstatus liegt in shared und fehlt im Modultest.
@TestPropertySource(properties = "management.endpoint.health.validate-group-membership=false")
public @interface ModuleTest {

}
