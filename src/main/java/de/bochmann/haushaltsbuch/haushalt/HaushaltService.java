package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Öffentliche API zum Anlegen eines Haushalts, genutzt von der Registrierung im Modul
 * {@code nutzer}.
 */
@Service
public class HaushaltService {

	private final HaushaltRepository haushaltRepository;

	HaushaltService(HaushaltRepository haushaltRepository) {
		this.haushaltRepository = haushaltRepository;
	}

	/**
	 * Legt einen Haushalt mit dem Nutzer als einzigem Mitglied an. Läuft in der Transaktion
	 * des Aufrufers, falls vorhanden.
	 *
	 * @param nutzerId Benutzer-ID aus Keycloak (Claim {@code sub})
	 * @param komponenten die gebuchten Komponenten, mindestens eine
	 * @return die ID des neuen Haushalts
	 * @throws org.springframework.dao.DataIntegrityViolationException wenn der Nutzer bereits
	 *         einem Haushalt angehört
	 */
	@Transactional
	public UUID anlegen(UUID nutzerId, Set<Komponente> komponenten) {
		return haushaltRepository.saveAndFlush(new Haushalt(nutzerId, komponenten, Instant.now())).getId();
	}

}
