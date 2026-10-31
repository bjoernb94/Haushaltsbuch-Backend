package de.bochmann.haushaltsbuch.shared.zeit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Uhr der Anwendung. Fachliche Daten (Buchungsdatum, Vertragsbeginn) sind {@code LocalDate};
 * „heute“ bestimmen Module mit {@code LocalDate.now(clock)}, damit kurz nach Mitternacht schon der
 * neue Tag gilt, auch wenn der Server in UTC läuft. Zeitstempel sind {@code Instant} und werden in
 * UTC gespeichert ({@code hibernate.jdbc.time_zone}).
 */
@Configuration(proxyBeanMethods = false)
class ZeitConfig {

	static final ZoneId ZEITZONE = ZoneId.of("Europe/Berlin");

	@Bean
	Clock clock() {
		return Clock.system(ZEITZONE);
	}

}
