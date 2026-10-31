package de.bochmann.haushaltsbuch.shared.fehler;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Daten wurden inzwischen von jemand anderem geändert: 409 mit Code {@value Probleme#KONFLIKT}.
 * Die SPA lädt den Datensatz dann neu.
 */
public class KonfliktException extends ErrorResponseException {

	/**
	 * @param detail englische Beschreibung für Entwickler
	 */
	public KonfliktException(String detail) {
		super(HttpStatus.CONFLICT, Probleme.problem(HttpStatus.CONFLICT, Probleme.KONFLIKT, detail), null);
	}

}
