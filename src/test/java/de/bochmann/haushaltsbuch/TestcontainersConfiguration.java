package de.bochmann.haushaltsbuch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL per Testcontainers, ein Container für alle Spring-Kontexte eines Testlaufs.
 * <p>
 * Bewusst kein Container-Bean mit {@code @ServiceConnection}: Spring Boot stoppt solche Container,
 * sobald ein Kontext geschlossen wird (z.&nbsp;B. durch {@code @DirtiesContext}), und der nächste
 * Kontext müsste einen neuen starten. Der Container wird hier einmal gestartet und am Ende der JVM
 * von Testcontainers (Ryuk) entfernt. Das Image kommt aus {@code compose.yaml} ({@link ComposeImages}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ComposeImages.ausEntwicklung("postgres"));

	static {
		POSTGRES.start();
	}

	@Bean
	DynamicPropertyRegistrar postgresProperties() {
		return registry -> {
			registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
			registry.add("spring.datasource.username", POSTGRES::getUsername);
			registry.add("spring.datasource.password", POSTGRES::getPassword);
		};
	}

}
