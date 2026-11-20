package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste beim Anlegen und Ändern. Die Regeln über die ganze Liste prüfen die Constraints an
 * {@link VertragAnfrage#preise()}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; beim frühesten Eintrag der Vertragsbeginn
 */
record VertragPreisAnfrage(
		@NotNull @PlausiblesDatum LocalDate gueltigAb,
		@NotNull @Valid @PositiverBetrag Betrag betrag) implements Preisangabe {

	VertragPreis vertragPreis() {
		return new VertragPreis(gueltigAb, betrag);
	}

}
