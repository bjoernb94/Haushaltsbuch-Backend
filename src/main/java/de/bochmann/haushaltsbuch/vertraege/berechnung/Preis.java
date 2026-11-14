package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; leer nur beim ersten Eintrag (ab Vertragsbeginn)
 */
public record Preis(@Nullable LocalDate gueltigAb, Betrag betrag) {
}
