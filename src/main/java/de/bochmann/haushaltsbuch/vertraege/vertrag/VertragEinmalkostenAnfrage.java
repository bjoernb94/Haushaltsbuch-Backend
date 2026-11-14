package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.shared.geld.PositiverBetrag;
import de.bochmann.haushaltsbuch.shared.zeit.PlausiblesDatum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Ein Eintrag der Einmalkosten beim Anlegen und Ändern. Die Währung prüft {@link GleicheWaehrung} am Vertrag (wie
 * der erste Preis). Fällig vor dem Beginn oder nach dem Ende ist erlaubt, z.&nbsp;B. vorab bezahlte Hardware.
 *
 * @param bezeichnung wofür die Kosten anfallen; im Formular „Wofür“
 */
record VertragEinmalkostenAnfrage(
		@NotBlank @Size(max = 200) String bezeichnung,
		@NotNull @Valid @PositiverBetrag Betrag betrag,
		@NotNull @PlausiblesDatum LocalDate faelligAm) {

	VertragEinmalkosten einmalkosten() {
		return new VertragEinmalkosten(bezeichnung.strip(), betrag, faelligAm);
	}

}
