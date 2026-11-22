package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/**
 * Ein Eintrag der {@link Preisliste} mit seiner {@link Preiszeilenart}, siehe {@link Vertragsberechnung#preiszeilen}.
 *
 * @param gueltigAb wie {@link Preis#gueltigAb()}
 * @param automatisch eine beim Speichern angelegte Stufe der Preissteigerung ({@link Preisherkunft#AUTOMATISCH})
 */
public record Preiszeile(LocalDate gueltigAb, Betrag betrag, Preiszeilenart art, boolean automatisch) {

	/** Eine eingetragene Zeile. */
	public Preiszeile(LocalDate gueltigAb, Betrag betrag, Preiszeilenart art) {
		this(gueltigAb, betrag, art, false);
	}

}
