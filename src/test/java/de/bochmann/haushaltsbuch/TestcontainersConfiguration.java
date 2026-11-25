package de.bochmann.haushaltsbuch;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import javax.sql.DataSource;

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

	/** Der gemeinsame Container, für Tests ohne Spring-Kontext mit eigener Datenbank (z.&nbsp;B. Migrationstests). */
	public static PostgreSQLContainer postgres() {
		return POSTGRES;
	}

	/**
	 * Legt im gemeinsamen Container eine neue, leere Datenbank an, für Tests ohne Spring-Kontext, die
	 * selbst migrieren (z.&nbsp;B. Migrationstests, {@code DatenmodellExportTests}).
	 *
	 * @param praefix Anfang des Namens, der Rest ist zufällig
	 */
	public static DataSource neueDatenbank(String praefix) {
		String name = praefix + "_" + UUID.randomUUID().toString().replace("-", "");
		new JdbcTemplate(datenquelle(POSTGRES.getDatabaseName())).execute("CREATE DATABASE " + name);
		return datenquelle(name);
	}

	private static DataSource datenquelle(String datenbank) {
		var quelle = new PGSimpleDataSource();
		quelle.setUrl(POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + datenbank));
		quelle.setUser(POSTGRES.getUsername());
		quelle.setPassword(POSTGRES.getPassword());
		return quelle;
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
