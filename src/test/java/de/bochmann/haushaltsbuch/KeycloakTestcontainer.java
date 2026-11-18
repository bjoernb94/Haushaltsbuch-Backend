package de.bochmann.haushaltsbuch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/**
 * Keycloak per Testcontainers mit unserem Realm-Import, für {@link KeycloakTest}. Image wie in
 * {@code compose.yaml} ({@link ComposeImages}), Client-Secret wie in dev.
 * <p>
 * Wie PostgreSQL ({@link TestcontainersConfiguration}) statisch gestartet, damit der Container nicht mit einem
 * Kontext stoppt. Bewusst ein einfacher {@link GenericContainer} statt des Moduls testcontainers-keycloak: Die
 * Tests sprechen nur über unseren eigenen Code mit Keycloak, dessen Admin-Client brauchen wir nicht.
 */
@TestConfiguration(proxyBeanMethods = false)
class KeycloakTestcontainer {

	private static final String CLIENT_SECRET = "dev-secret";

	private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(ComposeImages.ausEntwicklung("keycloak"))
			.withCommand("start-dev", "--import-realm")
			.withEnv("KEYCLOAK_CLIENT_SECRET", CLIENT_SECRET)
			.withCopyFileToContainer(MountableFile.forHostPath("docker/keycloak/import/haushaltsbuch-realm.json"),
					"/opt/keycloak/data/import/haushaltsbuch-realm.json")
			.withExposedPorts(8080)
			.waitingFor(Wait.forHttp("/realms/haushaltsbuch/.well-known/openid-configuration")
					.forPort(8080)
					.withStartupTimeout(Duration.ofMinutes(3)));

	static {
		KEYCLOAK.start();
	}

	@Bean
	DynamicPropertyRegistrar keycloakProperties() {
		return registry -> {
			String basis = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
			registry.add("haushaltsbuch.keycloak.issuer-uri", () -> basis + "/realms/haushaltsbuch");
			registry.add("haushaltsbuch.keycloak.admin-uri", () -> basis + "/admin/realms/haushaltsbuch");
			registry.add("haushaltsbuch.keycloak.client-secret", () -> CLIENT_SECRET);
		};
	}

}
