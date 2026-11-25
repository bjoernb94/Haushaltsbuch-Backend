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
 * Angaben des Formulars „Neuer Vertrag“. Der Status wird nicht mitgeschickt, sondern berechnet;
 * gekündigt ist ein Vertrag mit {@code gekuendigtZum}.
 *
 * @param bezeichnung leer, wenn der Anbieter als Name genügt
 * @param preise Preise mit Gültigkeitsdatum in beliebiger Reihenfolge, der früheste ab {@code beginn}; der heute
 *        gültige ist der Betrag des Vertrags
 * @param anteilProzent eigener Anteil an einem geteilten Vertrag (1–100, eine Nachkommastelle); leer = 100&nbsp;%.
 *        Übersichten zählen nur diesen Anteil der Kosten, Beträge und Abbuchungen bleiben die vollen
 * @param einmalkosten einmalige Kosten wie eine Anschlussgebühr in beliebiger Reihenfolge, auch leer; Pflicht, damit
 *        ein {@code PUT} ohne das Feld sie nicht still löscht. Sie zählen nicht zu den laufenden Kosten
 * @param preissteigerung „Preis steigt regelmäßig“; leer, wenn nicht. Daraus rechnet das Backend die Prognose
 * @param preisgarantieBis bis einschließlich dieses Tages rechnet die Prognose keine Erhöhung ein; auch ohne Regel
 * @param erinnerungSonderkuendigung „Vor jeder Preiserhöhung daran erinnern“, im Formular standardmäßig an; wird nur
 *        gespeichert und angezeigt
 * @param kuendbar {@code false} z.&nbsp;B. für den Rundfunkbeitrag; dann werden {@code verlaengerung} und
 *        {@code kuendigungsfristMonate} nicht gespeichert, {@code beginn} und {@code laufzeitende} bleiben (#67)
 * @param beginn Vertragsbeginn und Anker des Zahlungsrhythmus: ab diesem Tag wird im Takt der Zahlweise abgebucht
 * @param gekuendigtZum gesetzt, wenn gekündigt; ein vergangenes Datum erfasst einen beendeten Vertrag
 */
@LaufzeitendeNachBeginn
@PreisNichtVorBeginn
@ErsterPreisAbBeginn
@GleicheWaehrung
record VertragAnfrage(
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
		@Size(max = 1000) @Nullable String notiz) implements Vertragsangaben {
}
