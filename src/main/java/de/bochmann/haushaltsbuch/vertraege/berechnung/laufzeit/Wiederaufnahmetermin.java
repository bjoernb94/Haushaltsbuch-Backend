package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import java.time.LocalDate;

/**
 * Wann sich ein gekündigter oder beendeter Vertrag wieder aufnehmen lässt; siehe
 * {@link Laufzeitberechnung#wiederaufnahme}. Die SPA rechnet keine Daten (ADR 0010), sie zeigt diese.
 *
 * @param fruehestens der Tag nach dem Vertragsende; an ihm geht es nahtlos weiter, ohne Unterbrechung
 * @param vorschlag {@code fruehestens} oder heute, was später liegt; Vorbelegung im Dialog
 */
public record Wiederaufnahmetermin(LocalDate fruehestens, LocalDate vorschlag) {
}
