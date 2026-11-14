package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Preiszeile der Vorschau: geprüft wie {@link VertragPreisAnfrage}, aber ohne Pflicht. Ohne Betrag ignoriert die
 * Vorschau die Zeile.
 */
record VertragVorschauPreis(
		@PlausiblesDatum @Nullable LocalDate gueltigAb,
		@Valid @PositiverBetrag @Nullable Betrag betrag) implements Preisangabe {
}
