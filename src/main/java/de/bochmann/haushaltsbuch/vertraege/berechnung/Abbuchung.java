package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/** Eine Abbuchung mit dem an diesem Tag gültigen Preis. */
public record Abbuchung(LocalDate am, Betrag betrag) {
}
