package de.bochmann.haushaltsbuch.shared.fehler;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Gesuchte Daten existieren nicht: 404 mit Code {@value Probleme#NICHT_GEFUNDEN}. Auch für Daten eines
 * fremden Haushalts, damit sich nicht erkennen lässt, ob eine ID existiert.
 */
public class NichtGefundenException extends ErrorResponseException {

	/**
	 * @param detail englische Beschreibung für Entwickler, z.&nbsp;B. {@code "Buchung 42 not found."}
	 */
	public NichtGefundenException(String detail) {
		super(HttpStatus.NOT_FOUND, Probleme.problem(HttpStatus.NOT_FOUND, Probleme.NICHT_GEFUNDEN, detail), null);
	}

}
