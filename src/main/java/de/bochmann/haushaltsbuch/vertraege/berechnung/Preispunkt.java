package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/** Ein Punkt im Preisverlauf für das Diagramm; siehe {@link Vertragsberechnung#preisverlauf}. */
public record Preispunkt(LocalDate ab, Betrag betrag, Preispunktart art) {
}
