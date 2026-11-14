package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Ein Eintrag der Preisliste in der Antwort, wie in {@link VertragPreisAnfrage}.
 *
 * @param gueltigAb leer beim Eintrag, der ab Vertragsbeginn gilt
 */
record VertragPreisAntwort(@Nullable LocalDate gueltigAb, Betrag betrag) {

	static VertragPreisAntwort aus(VertragPreis preis) {
		return new VertragPreisAntwort(preis.gueltigAb(), preis.betrag());
	}

}
