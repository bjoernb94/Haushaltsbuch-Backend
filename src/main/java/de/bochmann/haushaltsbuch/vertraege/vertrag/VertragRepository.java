package de.bochmann.haushaltsbuch.vertraege.vertrag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Hibernate filtert alle Abfragen auf den aktuellen Haushalt ({@code FachEntity}). */
interface VertragRepository extends JpaRepository<Vertrag, UUID> {

}
