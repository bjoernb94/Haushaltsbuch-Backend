package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Eine Unterbrechung in der Antwort: von {@code von} bis einschließlich {@code bis} wurde nichts abgebucht. Beim
 * Ändern schickt die SPA die {@code id}s der verbleibenden als {@code unterbrechungIds} zurück.
 */
record VertragUnterbrechungAntwort(UUID id, LocalDate von, LocalDate bis, Unterbrechungsart art) {

	static VertragUnterbrechungAntwort aus(VertragUnterbrechung unterbrechung) {
		return new VertragUnterbrechungAntwort(unterbrechung.id(), unterbrechung.von(), unterbrechung.bis(),
				unterbrechung.art());
	}

}
