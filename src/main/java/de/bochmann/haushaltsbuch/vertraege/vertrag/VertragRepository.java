package de.bochmann.haushaltsbuch.vertraege.vertrag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Hibernate filtert alle Abfragen auf den aktuellen Haushalt ({@code FachEntity}). */
interface VertragRepository extends JpaRepository<Vertrag, UUID> {

	/** Mit Kategorie, damit die Liste nicht je Vertrag nachlädt. */
	@Override
	@EntityGraph(attributePaths = "kategorie")
	List<Vertrag> findAll();

}
