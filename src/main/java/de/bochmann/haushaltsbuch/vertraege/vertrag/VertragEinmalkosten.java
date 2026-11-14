package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Einmalkosten;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

/**
 * Ein Eintrag der Einmalkosten, als Zeile in {@code vertrag_einmalkosten} gespeichert. In der API
 * {@link VertragEinmalkostenAnfrage} bzw. {@link VertragEinmalkostenAntwort}.
 *
 * @param bezeichnung wofür die Kosten anfallen, z.&nbsp;B. „Anschlussgebühr“
 */
@Embeddable
record VertragEinmalkosten(
		@Column(name = "bezeichnung")
		String bezeichnung,
		@Embedded
		Betrag betrag,
		@Column(name = "faellig_am")
		LocalDate faelligAm) {

	Einmalkosten einmalkosten() {
		return new Einmalkosten(faelligAm, betrag);
	}

}
