package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;

import java.util.Set;

/**
 * Gebuchte Komponenten eines Haushalts mit der Version des Haushalts, gegen die eine Änderung
 * geprüft wird.
 */
public record GebuchteKomponenten(Set<Komponente> komponenten, long version) {

	public GebuchteKomponenten {
		komponenten = Set.copyOf(komponenten);
	}

	static GebuchteKomponenten aus(Haushalt haushalt) {
		return new GebuchteKomponenten(haushalt.getKomponenten(), haushalt.getVersion());
	}

}
