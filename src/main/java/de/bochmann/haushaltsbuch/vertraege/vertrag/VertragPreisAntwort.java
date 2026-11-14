package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeile;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste in der Antwort, wie in {@link VertragPreisAnfrage}.
 *
 * @param gueltigAb leer beim Eintrag, der ab Vertragsbeginn gilt
 * @param aktuell ob {@code betrag} des Vertrags aus diesem Eintrag stammt; das ist genau einer
 */
record VertragPreisAntwort(@Nullable LocalDate gueltigAb, Betrag betrag, boolean aktuell) {

	static VertragPreisAntwort aus(Preiszeile preis) {
		return new VertragPreisAntwort(preis.gueltigAb(), preis.betrag(), preis.aktuell());
	}

}
