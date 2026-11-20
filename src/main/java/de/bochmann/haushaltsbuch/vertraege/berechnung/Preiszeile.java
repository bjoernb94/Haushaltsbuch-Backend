package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste} mit seiner {@link Preiszeilenart}, siehe {@link Vertragsberechnung#preiszeilen}.
 *
 * @param gueltigAb wie {@link Preis#gueltigAb()}
 */
public record Preiszeile(LocalDate gueltigAb, Betrag betrag, Preiszeilenart art) {
}
