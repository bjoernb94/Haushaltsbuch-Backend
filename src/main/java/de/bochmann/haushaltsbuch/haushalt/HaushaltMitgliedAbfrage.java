package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltAbfrage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Liefert dem {@code HaushaltKontext} den Haushalt eines Nutzers. Liest per JDBC statt über
 * {@link HaushaltRepository}, weil Hibernate den Haushalt beim Öffnen einer Session erfragt.
 */
@Component
class HaushaltMitgliedAbfrage implements HaushaltAbfrage {

	private final JdbcClient jdbcClient;

	HaushaltMitgliedAbfrage(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public Optional<UUID> haushaltVon(UUID nutzerId) {
		return jdbcClient.sql("SELECT haushalt_id FROM haushalt_mitglied WHERE nutzer_id = ?")
				.param(nutzerId)
				.query(UUID.class)
				.optional();
	}

}
