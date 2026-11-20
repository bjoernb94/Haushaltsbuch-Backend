package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preis;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste, als Zeile in {@code vertrag_preis} gespeichert. In der API
 * {@link VertragPreisAnfrage} bzw. {@link VertragPreisAntwort}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; beim ersten Eintrag der Vertragsbeginn
 */
@Embeddable
record VertragPreis(
		@Column(name = "gueltig_ab")
		LocalDate gueltigAb,
		@Embedded
		Betrag betrag) {

	Preis preis() {
		return new Preis(gueltigAb, betrag);
	}

}
