package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste} mit seiner {@link Preiszeilenart}, siehe {@link Vertragsberechnung#preiszeilen}.
 *
 * @param gueltigAb wie {@link Preis#gueltigAb()}
 */
public record Preiszeile(@Nullable LocalDate gueltigAb, Betrag betrag, Preiszeilenart art) {
}
