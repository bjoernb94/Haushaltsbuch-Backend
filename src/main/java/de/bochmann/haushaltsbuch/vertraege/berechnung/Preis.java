package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; beim ersten Eintrag eines Vertrags sein Beginn
 */
public record Preis(LocalDate gueltigAb, Betrag betrag) {
}
