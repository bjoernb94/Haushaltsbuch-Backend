package de.bochmann.haushaltsbuch;

import org.junit.jupiter.api.TestInstance;
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
 * {@code @ApplicationModuleTest} setzt (über {@code @ModuleSlicing}) {@code @TestInstance(PER_CLASS)}, dann
 * teilen sich alle Tests einer Klasse eine Instanz und damit ihre Felder (#59). Hier steht deshalb wieder
 * der Standard von JUnit, eine Instanz je Testmethode wie bei {@link IntegrationTest}; JUnit findet die
 * Annotation hier vor der aus Spring Modulith.
 * <p>
 * Web- und Security-Tests (MockMvc, Filterkette) laufen dagegen mit {@link IntegrationTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ApplicationModuleTest(extraIncludes = "shared")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
public @interface ModuleTest {

}
