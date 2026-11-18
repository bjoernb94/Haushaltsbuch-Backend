package de.bochmann.haushaltsbuch;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Integrationstest gegen einen echten Keycloak ({@link KeycloakTestcontainer}): ganze Anwendung wie
 * {@link IntegrationTest}, aber ohne die Keycloak-Mocks. Prüft die Anbindung (Admin API, Token-Endpunkt,
 * Health), die {@link IntegrationTest} nur gemockt sieht, z.&nbsp;B. eine geänderte Prüfung in einem
 * Keycloak-Update.
 * <p>
 * Bewusst ein eigener Kontext. Damit er nur einmal startet, bleiben diese Tests in wenigen Klassen und ohne
 * eigene Mocks oder Properties. Der Realm ist für alle Tests derselbe: Jeder Test legt Konten mit eigener
 * E-Mail an.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({ TestcontainersConfiguration.class, KeycloakTestcontainer.class })
public @interface KeycloakTest {

}
