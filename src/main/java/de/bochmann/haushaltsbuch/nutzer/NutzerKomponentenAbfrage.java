package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Liefert der Security-Konfiguration die gebuchten Komponenten aus der eigenen Datenbank.
 */
@Component
class NutzerKomponentenAbfrage implements KomponentenAbfrage {

	private final NutzerRepository nutzerRepository;

	NutzerKomponentenAbfrage(NutzerRepository nutzerRepository) {
		this.nutzerRepository = nutzerRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Komponente> komponentenVon(UUID nutzerId) {
		return nutzerRepository.findKomponentenById(nutzerId);
	}

}
