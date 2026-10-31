package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Gemeinsame Konfiguration für Fachdaten: Auditing der {@link FachEntity} und Paging.
 * <p>
 * Liegt in {@code shared} und fehlt daher in Modultests ({@code @ModuleTest}); Tests, die
 * Fach-Entities speichern, laufen mit {@code @IntegrationTest}.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "angemeldeterNutzerAlsAuditor", dateTimeProviderRef = "auditZeit")
// Page wird als stabiles DTO ({"content": [...], "page": {...}}) ausgeliefert, nicht als PageImpl.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
class FachdatenConfig {

	@Bean
	AuditorAware<UUID> angemeldeterNutzerAlsAuditor() {
		return () -> AngemeldeterNutzer.id(SecurityContextHolder.getContext().getAuthentication());
	}

	/** Zeitpunkte über die {@link Clock} der Anwendung, damit Tests sie festlegen können. */
	@Bean
	DateTimeProvider auditZeit(Clock clock) {
		return () -> Optional.of(clock.instant());
	}

}
