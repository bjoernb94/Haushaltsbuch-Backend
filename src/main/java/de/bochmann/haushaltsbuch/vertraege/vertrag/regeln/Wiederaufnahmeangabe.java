package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;

import java.time.LocalDate;

/** Die Angaben des Dialogs „Wieder aufnehmen“ ({@code VertragWiederaufnahme}), die {@link Wiederaufnahmeregeln} prüft. */
public interface Wiederaufnahmeangabe {

	LocalDate datum();

	LocalDate laufzeitende();

	Verlaengerung verlaengerung();

	Betrag betrag();

}
