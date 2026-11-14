package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeile;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste in der Antwort, wie in {@link VertragPreisAnfrage}.
 *
 * @param gueltigAb leer beim Eintrag, der ab Vertragsbeginn gilt
 * @param art {@link Preiszeilenart#AKTUELL} bei genau dem Eintrag, aus dem {@code betrag} des Vertrags stammt
 */
record VertragPreisAntwort(@Nullable LocalDate gueltigAb, Betrag betrag, Preiszeilenart art) {

	static VertragPreisAntwort aus(Preiszeile preis) {
		return new VertragPreisAntwort(preis.gueltigAb(), preis.betrag(), preis.art());
	}

}
