package de.bochmann.haushaltsbuch;

import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test der Fachlogik eines Moduls (Services, Repositories): startet nur das Modul der Testklasse
 * ({@link ApplicationModuleTest}) und {@code shared}, mit Profil {@code dev} und PostgreSQL per
 * Testcontainers. Jedes Modul bekommt einen eigenen Spring-Kontext.
 * <p>
 * {@code shared} ist ausdrücklich eingebunden: Spring Modulith entfernt sonst alle Beans außerhalb
 * des getesteten Moduls, auch Konfiguration aus {@code shared} wie {@code FachdatenConfig}
 * (Auditing, Trennung der Haushalte) und das {@code Clock}-Bean. Beans anderer Module fehlen
 * weiterhin; braucht das Modul welche, schlägt der Start fehl, dann mit {@code @MockitoBean}
 * ersetzen. Fachmodule nutzen {@link FachmodulTest}, das die Schnittstellen von {@code shared}
 * ersetzt, die das Modul {@code haushalt} implementiert.
 * <p>
 * Web- und Security-Tests (MockMvc, Filterkette) laufen dagegen mit {@link IntegrationTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ApplicationModuleTest(extraIncludes = "shared")
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
public @interface ModuleTest {

}
