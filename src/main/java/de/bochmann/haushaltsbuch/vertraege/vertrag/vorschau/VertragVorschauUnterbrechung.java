package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Unterbrechungsangabe;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Unterbrechung in der Vorschau, wie in {@code VertragUnterbrechungAntwort} ohne {@code id}: die SPA schickt
 * die verbleibenden, ohne die im Formular zum Entfernen markierten. Unvollständige ignoriert die Vorschau.
 */
public record VertragVorschauUnterbrechung(
		@PlausiblesDatum @Nullable LocalDate von,
		@PlausiblesDatum @Nullable LocalDate bis,
		@Nullable Unterbrechungsart art) implements Unterbrechungsangabe {
}
