package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Preisangabe;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Preiszeile der Vorschau: geprüft wie {@code VertragPreisAnfrage}, aber ohne Pflicht. Ohne Datum oder Betrag
 * ignoriert die Vorschau die Zeile.
 *
 * @param automatisch wie in {@code VertragPreisAnfrage}; die Vorschau übernimmt es ungeprüft
 */
public record VertragVorschauPreis(
		@PlausiblesDatum @Nullable LocalDate gueltigAb,
		@Valid @PositiverBetrag @Nullable Betrag betrag,
		@Nullable Boolean automatisch) implements Preisangabe {
}
