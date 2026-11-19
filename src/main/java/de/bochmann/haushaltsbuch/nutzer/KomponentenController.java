package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Gebuchte Komponenten des Haushalts, dem der angemeldete Nutzer angehört. Die SPA blendet danach
 * Bereiche ein oder aus; den Zugriff selbst sichert die Security-Konfiguration ab, sie liest die
 * Komponenten bei jeder Anfrage, Änderungen greifen also sofort.
 */
@RestController
@Tag(name = "Nutzer")
@RequestMapping("/api/nutzer/ich/komponenten")
class KomponentenController {

	private final HaushaltService haushaltService;

	KomponentenController(HaushaltService haushaltService) {
		this.haushaltService = haushaltService;
	}

	/** Konto ohne Haushalt (nur nach Fehlern oder Eingriffen von Hand): 404. */
	@GetMapping
	KomponentenAntwort meineKomponenten(Authentication authentication) {
		return KomponentenAntwort.aus(haushaltService.komponenten(nutzerId(authentication)));
	}

	/**
	 * Ersetzt die Auswahl; die Fachdaten ausgeschalteter Komponenten bleiben erhalten. Veraltete
	 * {@code version}: 409, Konto ohne Haushalt: 404.
	 */
	@PutMapping
	KomponentenAntwort meineKomponentenAendern(Authentication authentication,
			@Valid @RequestBody KomponentenAenderung aenderung) {
		return KomponentenAntwort.aus(haushaltService.komponentenAendern(nutzerId(authentication),
				aenderung.komponenten(), aenderung.version()));
	}

	private static UUID nutzerId(Authentication authentication) {
		// Kann nicht anonym erreicht werden (SecurityConfig), nur ein Token ohne gültiges sub.
		return AngemeldeterNutzer.id(authentication)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
	}

}
