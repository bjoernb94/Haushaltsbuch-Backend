package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeile;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste in der Antwort, wie in {@link VertragPreisAnfrage}.
 *
 * @param art {@link Preiszeilenart#AKTUELL} bei genau dem Eintrag, aus dem {@code betrag} des Vertrags stammt (bzw.
 *        auf dem er aufbaut, wenn er errechnet ist)
 * @param automatisch vom Backend aus der Preissteigerung angelegt; beim Ändern unverändert zurückschicken
 */
record VertragPreisAntwort(LocalDate gueltigAb, Betrag betrag, Preiszeilenart art, boolean automatisch) {

	static VertragPreisAntwort aus(Preiszeile preis) {
		return new VertragPreisAntwort(preis.gueltigAb(), preis.betrag(), preis.art(), preis.automatisch());
	}

}
