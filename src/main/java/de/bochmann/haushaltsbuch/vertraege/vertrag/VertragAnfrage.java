package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
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
 * Angaben des Formulars „Neuer Vertrag“. Der Status wird nicht mitgeschickt, sondern berechnet;
 * gekündigt ist ein Vertrag mit {@code gekuendigtZum}.
 *
 * @param bezeichnung leer, wenn der Anbieter als Name genügt
 * @param preise Preise mit Gültigkeitsdatum in beliebiger Reihenfolge; der heute gültige ist der Betrag des Vertrags
 * @param abbuchungAb ab diesem Tag wird im Takt der Zahlweise abgebucht; im Formular „Nächste Abbuchung“
 * @param anteilProzent eigener Anteil an einem geteilten Vertrag (1–100, eine Nachkommastelle); leer = 100&nbsp;%.
 *        Übersichten zählen nur diesen Anteil der Kosten, Beträge und Abbuchungen bleiben die vollen
 * @param einmalkosten einmalige Kosten wie eine Anschlussgebühr in beliebiger Reihenfolge, auch leer; Pflicht, damit
 *        ein {@code PUT} ohne das Feld sie nicht still löscht. Sie zählen nicht zu den laufenden Kosten
 * @param kuendbar {@code false} z.&nbsp;B. für den Rundfunkbeitrag; dann werden {@code verlaengerung} und
 *        {@code kuendigungsfristMonate} nicht gespeichert, {@code beginn} und {@code laufzeitende} bleiben (#67)
 * @param gekuendigtZum gesetzt, wenn gekündigt; ein vergangenes Datum erfasst einen beendeten Vertrag
 */
@LaufzeitendeNachBeginn
@PreisNichtVorBeginn
@GleicheWaehrung
record VertragAnfrage(
		@NotBlank @Size(max = 200) String anbieter,
		@Size(max = 200) @Nullable String bezeichnung,
		@NotNull UUID kategorieId,
		@Size(max = 100) @Nullable String vertragsnummer,
		@NotEmpty @Size(max = 100) @GueltigAbPflicht @GueltigAbEindeutig @GleicheWaehrung
		List<@NotNull @Valid VertragPreisAnfrage> preise,
		@NotNull Zahlweise zahlweise,
		@NotNull @PlausiblesDatum LocalDate abbuchungAb,
		@Nullable Zahlungsweg zahlungsweg,
		@DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 1) @Nullable BigDecimal anteilProzent,
		@NotNull @Size(max = 100) List<@NotNull @Valid VertragEinmalkostenAnfrage> einmalkosten,
		@NotNull Boolean kuendbar,
		@PlausiblesDatum @Nullable LocalDate beginn,
		@PlausiblesDatum @Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Min(0) @Max(24) @Nullable Integer kuendigungsfristMonate,
		@PlausiblesDatum @Nullable LocalDate gekuendigtZum,
		@Size(max = 1000) @Nullable String notiz) implements Vertragsangaben {
}
