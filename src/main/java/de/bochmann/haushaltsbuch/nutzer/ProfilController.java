package de.bochmann.haushaltsbuch.nutzer;

import io.swagger.v3.oas.annotations.tags.Tag;
import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Profil des angemeldeten Nutzers. Name und E-Mail liegen in Keycloak und werden aus dem Token
 * der Anmeldung gelesen, ohne Aufruf bei Keycloak. Nutzer ändern diese Angaben nicht direkt in
 * Keycloak; ein späterer Profilbereich muss nach einer Änderung auch die Session aktualisieren.
 */
@RestController
@Tag(name = "Nutzer")
@RequestMapping("/api/nutzer/ich")
class ProfilController {

	record Profil(String vorname, String name, String email) {
	}

	@GetMapping
	Profil meinProfil(Authentication authentication) {
		Map<String, Object> claims = AngemeldeterNutzer.claims(authentication);
		// Kann nicht anonym erreicht werden (SecurityConfig); Absicherung für Tokens ohne Claims.
		if (claims.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return new Profil(text(claims, "given_name"), text(claims, "family_name"), text(claims, "email"));
	}

	private static String text(Map<String, Object> claims, String name) {
		return claims.get(name) instanceof String wert ? wert : null;
	}

}
