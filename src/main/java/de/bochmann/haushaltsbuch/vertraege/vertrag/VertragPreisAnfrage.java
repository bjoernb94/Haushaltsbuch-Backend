package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Preisangabe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Ein Eintrag der Preisliste beim Anlegen und Ändern. Die Regeln über die ganze Liste prüfen die Constraints an
 * {@link VertragAnfrage#preise()}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; beim frühesten Eintrag der Vertragsbeginn
 * @param automatisch {@code automatisch} aus der Antwort, unverändert zurückgeschickt; leer = {@code false}
 */
record VertragPreisAnfrage(
		@NotNull @PlausiblesDatum LocalDate gueltigAb,
		@NotNull @Valid @PositiverBetrag Betrag betrag,
		@Nullable Boolean automatisch) implements Preisangabe {

	/**
	 * Der zu speichernde Eintrag. Automatisch bleibt er nur, wenn es ihn mit gleichem Datum und Betrag schon als
	 * automatischen gibt; hat der Nutzer ihn geändert (oder kennt das Backend ihn nicht), ist er eingetragen.
	 *
	 * @param bisher die gespeicherten Preise des Vertrags; beim Anlegen leer
	 */
	VertragPreis vertragPreis(List<VertragPreis> bisher) {
		boolean bestaetigt = Boolean.TRUE.equals(automatisch) && bisher.contains(new VertragPreis(gueltigAb, betrag, true));
		return new VertragPreis(gueltigAb, betrag, bestaetigt);
	}

}
