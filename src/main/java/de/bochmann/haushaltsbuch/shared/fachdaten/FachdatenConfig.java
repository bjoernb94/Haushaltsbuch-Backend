package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
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
 * Gemeinsame Konfiguration für Fachdaten: Trennung der Haushalte ({@link HaushaltTenantResolver}),
 * Auditing der {@link FachEntity} und Paging.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "angemeldeterNutzerAlsAuditor", dateTimeProviderRef = "auditZeit")
// Page wird als stabiles DTO ({"content": [...], "page": {...}}) ausgeliefert, nicht als PageImpl.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
class FachdatenConfig {

	@Bean
	HibernatePropertiesCustomizer haushaltAlsTenant(HaushaltKontext haushaltKontext) {
		return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
				new HaushaltTenantResolver(haushaltKontext));
	}

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
