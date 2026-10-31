package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Liefert der Security-Konfiguration die gebuchten Komponenten des Haushalts, dem der Nutzer
 * angehört.
 */
@Component
class HaushaltKomponentenAbfrage implements KomponentenAbfrage {

	private final HaushaltRepository haushaltRepository;

	HaushaltKomponentenAbfrage(HaushaltRepository haushaltRepository) {
		this.haushaltRepository = haushaltRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Komponente> komponentenVon(UUID nutzerId) {
		return haushaltRepository.findKomponentenByMitglied(nutzerId);
	}

}
