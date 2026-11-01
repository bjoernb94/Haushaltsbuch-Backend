package de.bochmann.haushaltsbuch.nutzer;

import io.swagger.v3.oas.annotations.tags.Tag;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Gebuchte Komponenten des angemeldeten Nutzers. Die SPA blendet danach Bereiche ein oder
 * aus; den Zugriff selbst sichert die Security-Konfiguration ab.
 */
@RestController
@Tag(name = "Nutzer")
@RequestMapping("/api/nutzer/ich/komponenten")
class KomponentenController {

	private final KomponentenAbfrage komponentenAbfrage;

	KomponentenController(KomponentenAbfrage komponentenAbfrage) {
		this.komponentenAbfrage = komponentenAbfrage;
	}

	@GetMapping
	List<Komponente> meineKomponenten(Authentication authentication) {
		// Kann nicht anonym erreicht werden (SecurityConfig), nur ein Token ohne gültiges sub.
		var id = AngemeldeterNutzer.id(authentication)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
		return komponentenAbfrage.komponentenVon(id).stream().sorted().toList();
	}

}
