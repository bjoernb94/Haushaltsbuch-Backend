package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Steigerungsart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Die Preissteigerung in der Vorschau: geprüft wie {@link VertragPreissteigerungAnfrage}, aber ohne Pflicht und ohne
 * {@link PassenderWert}. Ist sie unvollständig, rechnet die Vorschau keine Prognose (siehe
 * {@link Steigerungsangabe#regel}).
 */
record VertragVorschauPreissteigerung(
		@Nullable Steigerungsart art,
		@DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
		@Nullable BigDecimal prozent,
		@Valid @PositiverBetrag @Nullable Betrag betrag,
		@Nullable Erhoehungsrhythmus rhythmus,
		@PlausiblesDatum @Nullable LocalDate naechsteAm) implements Steigerungsangabe {
}
