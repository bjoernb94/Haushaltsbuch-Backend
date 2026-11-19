package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface HaushaltRepository extends JpaRepository<Haushalt, UUID> {

	/** Liest nur die Komponenten, ohne den Haushalt zu laden; leer für unbekannte Nutzer. */
	@Query("select k from Haushalt h join h.mitglieder m join h.komponenten k where m = :nutzerId")
	Set<Komponente> findKomponentenByMitglied(UUID nutzerId);

	@Query("select h from Haushalt h join h.mitglieder m where m = :nutzerId")
	Optional<Haushalt> findByMitglied(UUID nutzerId);

}
