package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Instant;
import java.util.UUID;

/**
 * Registriert einen Nutzer in zwei Schritten: Konto im Identity Provider anlegen, dann in einer
 * Transaktion den Nutzer und seinen Haushalt mit den gewählten Komponenten speichern. Das Konto
 * lässt sich nicht in diese Transaktion einbeziehen; schlägt das Speichern fehl, wird es wieder
 * gelöscht.
 */
@Service
class RegistrierungService {

	private static final Logger log = LoggerFactory.getLogger(RegistrierungService.class);

	private final Identitaetsanbieter identitaetsanbieter;

	private final NutzerRepository nutzerRepository;

	private final HaushaltService haushaltService;

	private final TransactionOperations transaktion;

	RegistrierungService(Identitaetsanbieter identitaetsanbieter, NutzerRepository nutzerRepository,
			HaushaltService haushaltService, TransactionOperations transaktion) {
		this.identitaetsanbieter = identitaetsanbieter;
		this.nutzerRepository = nutzerRepository;
		this.haushaltService = haushaltService;
		this.transaktion = transaktion;
	}

	UUID registrieren(RegistrierungAnfrage anfrage) {
		UUID id = identitaetsanbieter.benutzerAnlegen(anfrage.email(), anfrage.passwort(),
				anfrage.vorname(), anfrage.name());
		try {
			transaktion.executeWithoutResult(status -> {
				nutzerRepository.saveAndFlush(new Nutzer(id, Instant.now()));
				haushaltService.anlegen(id, anfrage.komponenten());
			});
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
