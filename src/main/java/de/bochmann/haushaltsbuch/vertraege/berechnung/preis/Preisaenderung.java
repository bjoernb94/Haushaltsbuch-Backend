package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/** Die nächste bekannte Preisänderung eines Vertrags; siehe {@link Preisliste#naechsteAenderung}. */
public record Preisaenderung(LocalDate ab, Betrag betrag, Preisaenderungsart art) {
}
