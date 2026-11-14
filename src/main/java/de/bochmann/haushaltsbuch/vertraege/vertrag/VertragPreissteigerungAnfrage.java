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
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Die Regel „Preis steigt regelmäßig“ beim Anlegen und Ändern. Die Währung von {@code betrag} prüft
 * {@link GleicheWaehrung} am Vertrag (wie der erste Preis).
 *
 * @param prozent nur bei {@link Steigerungsart#PROZENT}, z.&nbsp;B. 2,5
 * @param betrag nur bei {@link Steigerungsart#BETRAG}
 * @param naechsteAm Datum der nächsten Erhöhung, Anker im Rhythmus wie {@code abbuchungAb}; darf vergangen sein
 */
@PassenderWert
record VertragPreissteigerungAnfrage(
		@NotNull Steigerungsart art,
		@DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
		@Nullable BigDecimal prozent,
		@Valid @PositiverBetrag @Nullable Betrag betrag,
		@NotNull Erhoehungsrhythmus rhythmus,
		@NotNull @PlausiblesDatum LocalDate naechsteAm) implements Steigerungsangabe {

	/** Mit fester Nachkommastelle wie aus der Datenbank (2.5 → 2.50), damit die Antwort beim Anlegen dieselbe ist. */
	VertragPreissteigerung preissteigerung() {
		return new VertragPreissteigerung(art, prozent != null ? prozent.setScale(2) : null, betrag, rhythmus,
				naechsteAm);
	}

}
