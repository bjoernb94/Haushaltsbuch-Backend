package de.bochmann.haushaltsbuch.vertraege.kategorie;

import java.util.UUID;

/**
 * Kategorie in Antworten. Die SPA zeigt bei {@code schluessel} ihren übersetzten Text, sonst
 * {@code name} (eigene Kategorien, #34).
 */
public record VertragKategorieAntwort(UUID id, String schluessel, String name) {

	public static VertragKategorieAntwort aus(VertragKategorie kategorie) {
		return new VertragKategorieAntwort(kategorie.getId(), kategorie.getSchluessel(), kategorie.getName());
	}

}
