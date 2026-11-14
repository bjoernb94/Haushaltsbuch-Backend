package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste beim Anlegen und Ändern. Die Regeln über die ganze Liste prüfen die Constraints an
 * {@link VertragAnfrage#preise()}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; leer nur bei einem Eintrag (= ab Vertragsbeginn)
 */
record VertragPreisAnfrage(
		@PlausiblesDatum @Nullable LocalDate gueltigAb,
		@NotNull @Valid @PositiverBetrag Betrag betrag) {

	VertragPreis preis() {
		return new VertragPreis(gueltigAb, betrag);
	}

}
