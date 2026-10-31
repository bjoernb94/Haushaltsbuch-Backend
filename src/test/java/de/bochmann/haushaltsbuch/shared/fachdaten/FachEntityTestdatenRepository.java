package de.bochmann.haushaltsbuch.shared.fachdaten;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface FachEntityTestdatenRepository extends JpaRepository<FachEntityTestdaten, UUID> {

}
