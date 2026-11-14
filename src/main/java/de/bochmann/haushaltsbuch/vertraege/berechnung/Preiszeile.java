package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste} mit der Angabe, ob er der aktuelle ist, siehe
 * {@link Vertragsberechnung#preiszeilen}.
 *
 * @param gueltigAb wie {@link Preis#gueltigAb()}
 * @param aktuell ob {@link Vertragswerte#betrag()} aus diesem Eintrag stammt; das ist genau einer
 */
public record Preiszeile(@Nullable LocalDate gueltigAb, Betrag betrag, boolean aktuell) {
}
