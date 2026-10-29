package de.bochmann.haushaltsbuch.nutzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Registriert einen Nutzer in zwei Schritten: Konto im Identity Provider anlegen, dann den
 * Nutzer mit seinen Komponenten speichern. Beide Schritte lassen sich nicht in einer
 * Transaktion zusammenfassen; schlägt das Speichern fehl, wird das Konto wieder gelöscht.
 */
@Service
class RegistrierungService {

	private static final Logger log = LoggerFactory.getLogger(RegistrierungService.class);

	private final Identitaetsanbieter identitaetsanbieter;

	private final NutzerRepository nutzerRepository;

	RegistrierungService(Identitaetsanbieter identitaetsanbieter, NutzerRepository nutzerRepository) {
		this.identitaetsanbieter = identitaetsanbieter;
		this.nutzerRepository = nutzerRepository;
	}

	UUID registrieren(RegistrierungAnfrage anfrage) {
		UUID id = identitaetsanbieter.benutzerAnlegen(anfrage.email(), anfrage.passwort(),
				anfrage.vorname(), anfrage.name());
		try {
			nutzerRepository.save(new Nutzer(id, anfrage.komponenten(), Instant.now()));
		}
		catch (RuntimeException e) {
			log.warn("Nutzer {} konnte nicht gespeichert werden, Konto wird wieder gelöscht.", id, e);
			try {
				identitaetsanbieter.benutzerLoeschen(id);
			}
			catch (RuntimeException loeschfehler) {
				log.error("Konto {} konnte nicht gelöscht werden und muss manuell entfernt werden.", id,
						loeschfehler);
				e.addSuppressed(loeschfehler);
			}
			throw e;
		}
		return id;
	}

}
