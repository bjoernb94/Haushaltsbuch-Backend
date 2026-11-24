package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Angaben des Dialogs „Wieder aufnehmen“ für einen gekündigten oder beendeten Vertrag. Die Regeln, die den
 * gespeicherten Vertrag brauchen (nach dem Vertragsende, Laufzeit bei kündbaren, gleiche Währung, kein zweiter
 * eingetragener Preis am Tag), prüft der Service; siehe {@code docs/api.md}.
 *
 * @param version wie beim Ändern
 * @param datum erster Tag nach der Unterbrechung; liegt er direkt nach dem Vertragsende, geht es ohne Unterbrechung
 *        weiter
 * @param laufzeitende neue Mindestlaufzeit; bei nicht kündbaren Verträgen ignoriert
 * @param verlaengerung Pflicht bei kündbaren Verträgen, bei nicht kündbaren ignoriert
 * @param kuendigungsfristMonate bei nicht kündbaren Verträgen ignoriert
 * @param betrag neuer Preis ab {@code datum}; leer, wenn er sich nicht ändert
 */
record VertragWiederaufnahme(
		@NotNull Long version,
		@NotNull @PlausiblesDatum LocalDate datum,
		@PlausiblesDatum @Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Min(0) @Max(24) @Nullable Integer kuendigungsfristMonate,
		@Valid @PositiverBetrag @Nullable Betrag betrag) {
}
