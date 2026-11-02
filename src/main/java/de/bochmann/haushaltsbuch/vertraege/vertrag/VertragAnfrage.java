package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Angaben des Formulars „Neuer Vertrag“. Der Status wird nicht mitgeschickt, sondern berechnet;
 * gekündigt ist ein Vertrag mit {@code gekuendigtZum}.
 *
 * @param bezeichnung leer, wenn der Anbieter als Name genügt
 * @param abbuchungAb ab diesem Tag wird im Takt der Zahlweise abgebucht; im Formular „Nächste Abbuchung“
 * @param kuendbar {@code false} z.&nbsp;B. für den Rundfunkbeitrag; dann werden {@code verlaengerung} und
 *        {@code kuendigungsfristMonate} nicht gespeichert, {@code beginn} und {@code laufzeitende} bleiben (#67)
 * @param gekuendigtZum gesetzt, wenn gekündigt; ein vergangenes Datum erfasst einen beendeten Vertrag
 */
@LaufzeitendeNachBeginn
record VertragAnfrage(
		@NotBlank @Size(max = 200) String anbieter,
		@Size(max = 200) @Nullable String bezeichnung,
		@NotNull UUID kategorieId,
		@Size(max = 100) @Nullable String vertragsnummer,
		@NotNull @Valid @PositiverBetrag Betrag betrag,
		@NotNull Zahlweise zahlweise,
		@NotNull @PlausiblesDatum LocalDate abbuchungAb,
		@Nullable Zahlungsweg zahlungsweg,
		@NotNull Boolean kuendbar,
		@PlausiblesDatum @Nullable LocalDate beginn,
		@PlausiblesDatum @Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Min(0) @Max(24) @Nullable Integer kuendigungsfristMonate,
		@PlausiblesDatum @Nullable LocalDate gekuendigtZum,
		@Size(max = 1000) @Nullable String notiz) implements Vertragsangaben {
}
