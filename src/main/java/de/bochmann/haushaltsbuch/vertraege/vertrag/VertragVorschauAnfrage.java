package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Die Formularwerte für die Vorschau des Kündigungstermins. Bedeutung und Prüfungen wie in {@link VertragAnfrage},
 * aber außer {@code kuendbar} darf alles fehlen, weil das Formular noch halb ausgefüllt sein kann.
 */
@LaufzeitendeNachBeginn
record VertragVorschauAnfrage(
		Zahlweise zahlweise,
		@PlausiblesDatum LocalDate abbuchungAb,
		@NotNull Boolean kuendbar,
		@PlausiblesDatum LocalDate beginn,
		@PlausiblesDatum LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		@Min(0) @Max(24) Integer kuendigungsfristMonate,
		@PlausiblesDatum LocalDate gekuendigtZum) implements Berechnungsangaben {
}
