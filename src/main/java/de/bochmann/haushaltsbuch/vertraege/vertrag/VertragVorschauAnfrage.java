package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Die Formularwerte für die Vorschau (Zusammenfassung und Statuszeilen). Bedeutung und Prüfungen wie in
 * {@link VertragAnfrage}, aber außer {@code kuendbar} darf alles fehlen, weil das Formular noch halb ausgefüllt sein
 * kann: Ein fehlender Wert ist kein Fehler, nur das Ergebnis, das ihn braucht, bleibt leer. Ohne {@code beginn} rechnet
 * sie ohne Preise, weil der erste ab dem Beginn gilt. Unvollständige Zeilen (Preis ohne Datum oder Betrag,
 * Einmalkosten ohne Bezeichnung, Betrag oder Fälligkeit, Preissteigerung ohne alle Angaben) ignoriert die Vorschau.
 * Ungültige Werte lehnt sie ab wie das Speichern, damit sie nichts für Werte zeigt, die sich nicht speichern lassen.
 *
 * @param preissteigerungBetrachtetBis aus der Antwort des gespeicherten Vertrags, unverändert; leer bei einem neuen
 * @param unterbrechungen die verbleibenden des gespeicherten Vertrags, ohne die im Formular zum Entfernen markierten;
 *        leer bei einem neuen. Kündigung und Laufzeit zählen ab der letzten Wiederaufnahme
 */
@LaufzeitendeNachBeginn
@PreisNichtVorBeginn
@ErsterPreisAbBeginn
@GleicheWaehrung
@BeginnVorUnterbrechung
@KuendigungNachUnterbrechung
@LaufzeitendeNachAktuellemBeginn
record VertragVorschauAnfrage(
		@Size(max = Preisangabe.HOECHSTENS) @GueltigAbEindeutig @GleicheWaehrung
		@Nullable List<@Valid VertragVorschauPreis> preise,
		@Nullable Zahlweise zahlweise,
		@DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 1) @Nullable BigDecimal anteilProzent,
		@Size(max = 100) @Nullable List<@Valid VertragVorschauEinmalkosten> einmalkosten,
		@Valid @Nullable VertragVorschauPreissteigerung preissteigerung,
		@PlausiblesDatum @Nullable LocalDate preisgarantieBis,
		@PlausiblesDatum @Nullable LocalDate preissteigerungBetrachtetBis,
		@NotNull Boolean kuendbar,
		@PlausiblesDatum @Nullable LocalDate beginn,
		@PlausiblesDatum @Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Min(0) @Max(24) @Nullable Integer kuendigungsfristMonate,
		@PlausiblesDatum @Nullable LocalDate gekuendigtZum,
		@Size(max = 100) @UnterbrechungenGetrennt @Nullable List<@Valid VertragVorschauUnterbrechung> unterbrechungen)
		implements Berechnungsangaben {

	/** Nur die vollständigen, sortiert nach {@code von}. */
	@Override
	public List<Unterbrechung> unterbrechungenFuerBerechnung() {
		return unterbrechungen == null ? List.of() : unterbrechungen.stream().filter(Objects::nonNull)
				.filter(VertragVorschauUnterbrechung::vollstaendig).map(VertragVorschauUnterbrechung::unterbrechung)
				.sorted(Comparator.comparing(Unterbrechung::von))
				.toList();
	}

}
