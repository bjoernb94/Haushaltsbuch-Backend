package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Einmalkostenangabe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Zeile der Einmalkosten in der Vorschau: geprüft wie {@code VertragEinmalkostenAnfrage}, aber ohne Pflicht.
 * Ohne Bezeichnung, Betrag oder Fälligkeit ignoriert die Vorschau die Zeile.
 */
public record VertragVorschauEinmalkosten(
		@Size(max = 200) @Nullable String bezeichnung,
		@Valid @PositiverBetrag @Nullable Betrag betrag,
		@PlausiblesDatum @Nullable LocalDate faelligAm) implements Einmalkostenangabe {
}
