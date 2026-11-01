package de.bochmann.haushaltsbuch.vertraege.kategorie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Kategorie eines Vertrags. Standardkategorien gelten für alle Haushalte ({@code haushaltId} leer) und
 * haben einen {@code schluessel}, den die SPA übersetzt; eigene Kategorien eines Haushalts (#34) einen
 * {@code name}. Keine {@code FachEntity}: Die Tabelle mischt beide Arten, gefiltert wird in
 * {@link VertragKategorieRepository}.
 */
@Entity
@Table(name = "vertrag_kategorie")
public class VertragKategorie {

	@Id
	private UUID id;

	@Column(name = "haushalt_id")
	private UUID haushaltId;

	@Column(name = "schluessel", length = 40)
	private String schluessel;

	@Column(name = "name", length = 100)
	private String name;

	@Column(name = "reihenfolge", nullable = false)
	private int reihenfolge;

	protected VertragKategorie() {
	}

	public UUID getId() {
		return id;
	}

	public String getSchluessel() {
		return schluessel;
	}

	public String getName() {
		return name;
	}

}
