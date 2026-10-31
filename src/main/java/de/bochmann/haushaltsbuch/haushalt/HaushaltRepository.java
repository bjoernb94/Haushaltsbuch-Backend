package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface HaushaltRepository extends JpaRepository<Haushalt, UUID> {

	/** Leer, wenn der Nutzer keinem Haushalt angehört. */
	@Query("select h.id from Haushalt h join h.mitglieder m where m = :nutzerId")
	Optional<UUID> findIdByMitglied(UUID nutzerId);

	/** Liest nur die Komponenten, ohne den Haushalt zu laden; leer für unbekannte Nutzer. */
	@Query("select k from Haushalt h join h.mitglieder m join h.komponenten k where m = :nutzerId")
	Set<Komponente> findKomponentenByMitglied(UUID nutzerId);

}
