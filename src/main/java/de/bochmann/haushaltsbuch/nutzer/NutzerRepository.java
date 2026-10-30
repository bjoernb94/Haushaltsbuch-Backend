package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.Komponente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

interface NutzerRepository extends JpaRepository<Nutzer, UUID> {

	/** Liest nur die Komponenten, ohne den Nutzer zu laden; leer für unbekannte Nutzer. */
	@Query("select k from Nutzer n join n.komponenten k where n.id = :id")
	Set<Komponente> findKomponentenById(UUID id);

}
