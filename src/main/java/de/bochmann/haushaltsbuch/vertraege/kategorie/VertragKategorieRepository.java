package de.bochmann.haushaltsbuch.vertraege.kategorie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Liest nur Standardkategorien und die des übergebenen Haushalts. */
interface VertragKategorieRepository extends JpaRepository<VertragKategorie, UUID> {

	@Query("""
			select k from VertragKategorie k
			where k.haushaltId is null or k.haushaltId = :haushaltId
			order by k.reihenfolge, k.name""")
	List<VertragKategorie> findVerwendbare(UUID haushaltId);

	@Query("select k from VertragKategorie k where k.id = :id and (k.haushaltId is null or k.haushaltId = :haushaltId)")
	Optional<VertragKategorie> findVerwendbare(UUID id, UUID haushaltId);

}
