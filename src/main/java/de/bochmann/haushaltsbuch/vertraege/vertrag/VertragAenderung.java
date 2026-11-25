package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.ErsterPreisAbBeginn;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.GleicheWaehrung;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.GueltigAbEindeutig;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.LaufzeitendeNachBeginn;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.PreisNichtVorBeginn;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Preisangabe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Angaben des Formulars „Vertrag bearbeiten“: alle Felder wie in {@link VertragAnfrage} (dort ihre
 * Bedeutung) plus die {@code version} aus der Antwort, gegen die der Service prüft, ob inzwischen
 * jemand anderes geändert hat. Ein eigenes Schema, damit {@code version} in der OpenAPI Pflicht ist
 * und beim Anlegen nicht auftaucht; die Feldliste muss mit {@link VertragAnfrage} übereinstimmen. Dazu kommen
 * am Ende {@code unterbrechungIds}.
 *
 * @param unterbrechungIds die IDs der Unterbrechungen, die bleiben sollen (auch leer); fehlende werden gelöscht.
 *        Pflicht, damit ein {@code PUT} ohne das Feld sie nicht still löscht. Neue entstehen nur über die
 *        Wiederaufnahme
 */
@LaufzeitendeNachBeginn
@PreisNichtVorBeginn
@ErsterPreisAbBeginn
@GleicheWaehrung
record VertragAenderung(
		@NotNull Long version,
		@NotBlank @Size(max = 200) String anbieter,
		@Size(max = 200) @Nullable String bezeichnung,
		@NotNull UUID kategorieId,
		@Size(max = 100) @Nullable String vertragsnummer,
		@NotEmpty @Size(max = Preisangabe.HOECHSTENS) @GueltigAbEindeutig @GleicheWaehrung
		List<@NotNull @Valid VertragPreisAnfrage> preise,
		@NotNull Zahlweise zahlweise,
		@Nullable Zahlungsweg zahlungsweg,
		@DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 1) @Nullable BigDecimal anteilProzent,
		@NotNull @Size(max = 100) List<@NotNull @Valid VertragEinmalkostenAnfrage> einmalkosten,
		@Valid @Nullable VertragPreissteigerungAnfrage preissteigerung,
		@PlausiblesDatum @Nullable LocalDate preisgarantieBis,
		@NotNull Boolean erinnerungSonderkuendigung,
		@NotNull Boolean kuendbar,
		@NotNull @PlausiblesDatum LocalDate beginn,
		@PlausiblesDatum @Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Min(0) @Max(24) @Nullable Integer kuendigungsfristMonate,
		@PlausiblesDatum @Nullable LocalDate gekuendigtZum,
		@Size(max = 1000) @Nullable String notiz,
		@NotNull List<@NotNull UUID> unterbrechungIds) implements Vertragsangaben {
}
