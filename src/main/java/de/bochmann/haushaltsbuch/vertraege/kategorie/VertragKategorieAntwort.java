package de.bochmann.haushaltsbuch.vertraege.kategorie;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Kategorie in Antworten. Die SPA zeigt bei {@code schluessel} ihren übersetzten Text, sonst
 * {@code name} (eigene Kategorien, #34).
 */
public record VertragKategorieAntwort(UUID id, @Nullable VertragKategorieSchluessel schluessel, @Nullable String name) {

	public static VertragKategorieAntwort aus(VertragKategorie kategorie) {
		return new VertragKategorieAntwort(kategorie.getId(), kategorie.getSchluessel(), kategorie.getName());
	}

}
