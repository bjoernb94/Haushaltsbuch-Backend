package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Angaben des Formulars „Vertrag bearbeiten“: alle Felder wie in {@link VertragAnfrage} (dort ihre
 * Bedeutung) plus die {@code version} aus der Antwort, gegen die der Service prüft, ob inzwischen
 * jemand anderes geändert hat. Ein eigenes Schema, damit {@code version} in der OpenAPI Pflicht ist
 * und beim Anlegen nicht auftaucht; die Feldliste muss mit {@link VertragAnfrage} übereinstimmen.
 */
@LaufzeitendeNachBeginn
record VertragAenderung(
		@NotNull Long version,
		@NotBlank @Size(max = 200) String anbieter,
		@Size(max = 200) String bezeichnung,
		@NotNull UUID kategorieId,
		@Size(max = 100) String vertragsnummer,
		@NotNull @Valid @PositiverBetrag Betrag betrag,
		@NotNull Zahlweise zahlweise,
		@NotNull LocalDate abbuchungAb,
		Zahlungsweg zahlungsweg,
		@NotNull Boolean kuendbar,
		LocalDate beginn,
		LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		@Min(0) @Max(24) Integer kuendigungsfristMonate,
		LocalDate gekuendigtZum,
		@Size(max = 1000) String notiz) implements Vertragsangaben {
}
