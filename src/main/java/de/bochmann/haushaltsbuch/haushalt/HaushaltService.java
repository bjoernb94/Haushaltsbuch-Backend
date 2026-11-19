package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Öffentliche API zum Anlegen eines Haushalts und zum Ändern seiner Komponenten, genutzt vom Modul
 * {@code nutzer} (Registrierung, Einstellungen).
 */
@Service
public class HaushaltService {

	private final HaushaltRepository haushaltRepository;

	private final Clock clock;

	HaushaltService(HaushaltRepository haushaltRepository, Clock clock) {
		this.haushaltRepository = haushaltRepository;
		this.clock = clock;
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
		return haushaltRepository.saveAndFlush(new Haushalt(nutzerId, komponenten, clock.instant())).getId();
	}

	/**
	 * @param nutzerId Benutzer-ID aus Keycloak (Claim {@code sub})
	 * @throws NichtGefundenException wenn der Nutzer keinem Haushalt angehört (404)
	 */
	@Transactional(readOnly = true)
	public GebuchteKomponenten komponenten(UUID nutzerId) {
		return GebuchteKomponenten.aus(haushaltVon(nutzerId));
	}

	/**
	 * Ersetzt die gebuchten Komponenten des Haushalts, dem der Nutzer angehört. Die Fachdaten
	 * ausgeschalteter Komponenten bleiben erhalten, nur der Zugriff ist gesperrt.
	 *
	 * @param nutzerId Benutzer-ID aus Keycloak (Claim {@code sub})
	 * @param komponenten die neue Auswahl, mindestens eine
	 * @param version die Version, die der Client zuletzt gesehen hat
	 * @return die neue Auswahl mit der neuen Version
	 * @throws NichtGefundenException wenn der Nutzer keinem Haushalt angehört (404)
	 * @throws de.bochmann.haushaltsbuch.shared.fehler.KonfliktException bei veralteter Version (409)
	 */
	@Transactional
	public GebuchteKomponenten komponentenAendern(UUID nutzerId, Set<Komponente> komponenten, long version) {
		var haushalt = haushaltVon(nutzerId);
		haushalt.pruefeVersion(version);
		haushalt.komponentenSetzen(komponenten);
		// Hibernate erhöht die Version erst beim Schreiben; die Antwort braucht schon die neue.
		haushaltRepository.flush();
		return GebuchteKomponenten.aus(haushalt);
	}

	private Haushalt haushaltVon(UUID nutzerId) {
		return haushaltRepository.findByMitglied(nutzerId)
				.orElseThrow(() -> new NichtGefundenException("No household for user %s.".formatted(nutzerId)));
	}

}
