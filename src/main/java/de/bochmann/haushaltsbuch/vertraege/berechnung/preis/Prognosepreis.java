package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/** Ein prognostizierter Preis, siehe {@link Preisprognose}. */
public record Prognosepreis(LocalDate ab, Betrag betrag) {
}
