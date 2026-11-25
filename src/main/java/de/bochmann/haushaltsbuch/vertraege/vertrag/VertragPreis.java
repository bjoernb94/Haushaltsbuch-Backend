package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste, als Zeile in {@code vertrag_preis} gespeichert. In der API
 * {@link VertragPreisAnfrage} bzw. {@link VertragPreisAntwort}.
 *
 * @param gueltigAb ab diesem Tag gilt der Preis; beim ersten Eintrag der Vertragsbeginn
 * @param automatisch eine beim Speichern angelegte Stufe der Preissteigerung (ADR 0022)
 */
@Embeddable
record VertragPreis(
		@Column(name = "gueltig_ab")
		LocalDate gueltigAb,
		@Embedded
		Betrag betrag,
		@Column(name = "automatisch")
		boolean automatisch) {

	/** Eine fällige Stufe, die das Speichern anlegt. */
	static VertragPreis stufe(Preis preis) {
		return new VertragPreis(preis.gueltigAb(), preis.betrag(), true);
	}

	Preis preis() {
		return new Preis(gueltigAb, betrag, automatisch ? Preisherkunft.AUTOMATISCH : Preisherkunft.EINGETRAGEN);
	}

}
