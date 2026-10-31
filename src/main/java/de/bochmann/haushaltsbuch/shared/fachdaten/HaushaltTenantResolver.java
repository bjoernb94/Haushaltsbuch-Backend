package de.bochmann.haushaltsbuch.shared.fachdaten;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import java.util.UUID;

/**
 * Teilt Hibernate beim Öffnen jeder Session den Haushalt mit; Hibernate filtert damit alle
 * Abfragen auf {@link FachEntity}s (auch {@code findById}) und setzt {@code haushalt_id} beim
 * Anlegen. Registriert in {@link FachdatenConfig}.
 */
class HaushaltTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

	private final HaushaltKontext haushaltKontext;

	HaushaltTenantResolver(HaushaltKontext haushaltKontext) {
		this.haushaltKontext = haushaltKontext;
	}

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		return haushaltKontext.id().orElse(HaushaltKontext.KEIN_HAUSHALT);
	}

	/** Spring verwaltet die Sessions selbst ({@code getCurrentSession} wird nicht genutzt). */
	@Override
	public boolean validateExistingCurrentSessions() {
		return false;
	}

}
