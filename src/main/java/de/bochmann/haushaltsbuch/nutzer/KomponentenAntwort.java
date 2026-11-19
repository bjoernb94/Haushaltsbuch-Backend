package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.haushalt.GebuchteKomponenten;
import de.bochmann.haushaltsbuch.shared.Komponente;

import java.util.List;

/**
 * Gebuchte Komponenten des Haushalts, sortiert, mit der {@code version} des Haushalts für
 * {@link KomponentenAenderung}.
 */
record KomponentenAntwort(List<Komponente> komponenten, long version) {

	static KomponentenAntwort aus(GebuchteKomponenten gebucht) {
		return new KomponentenAntwort(gebucht.komponenten().stream().sorted().toList(), gebucht.version());
	}

}
