package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Ermittelt den Haushalt des angemeldeten Nutzers. Fachmodule speichern ihre Daten mit dieser
 * {@code haushalt_id} und filtern jeden Zugriff darauf; Daten eines fremden Haushalts beantworten
 * sie mit 404, nicht 403.
 */
@Component
public class AktuellerHaushalt {

	private final HaushaltRepository haushaltRepository;

	AktuellerHaushalt(HaushaltRepository haushaltRepository) {
		this.haushaltRepository = haushaltRepository;
	}

	/**
	 * @return die ID des Haushalts, dem der angemeldete Nutzer angehört
	 * @throws ResponseStatusException 401 bei anonymen Anfragen (nur ohne Profil {@code keycloak}
	 *         möglich), 403 wenn der Nutzer keinem Haushalt angehört (z.&nbsp;B. Konto direkt in
	 *         Keycloak angelegt)
	 */
	@Transactional(readOnly = true)
	public UUID id(Authentication authentication) {
		UUID nutzerId = AngemeldeterNutzer.id(authentication)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
		return haushaltRepository.findIdByMitglied(nutzerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
	}

}
