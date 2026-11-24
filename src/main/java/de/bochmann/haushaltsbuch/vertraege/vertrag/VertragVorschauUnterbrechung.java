package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Unterbrechungsart;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Unterbrechung in der Vorschau, wie in {@link VertragUnterbrechungAntwort} ohne {@code id}: die SPA schickt
 * die verbleibenden, ohne die im Formular zum Entfernen markierten. Unvollständige ignoriert die Vorschau.
 */
record VertragVorschauUnterbrechung(
		@PlausiblesDatum @Nullable LocalDate von,
		@PlausiblesDatum @Nullable LocalDate bis,
		@Nullable Unterbrechungsart art) {

	boolean vollstaendig() {
		return von != null && bis != null && art != null;
	}

	Unterbrechung unterbrechung() {
		return new Unterbrechung(von, bis, art);
	}

}
