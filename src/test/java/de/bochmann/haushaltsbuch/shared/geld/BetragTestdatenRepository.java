package de.bochmann.haushaltsbuch.shared.geld;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface BetragTestdatenRepository extends JpaRepository<BetragTestdaten, UUID> {

}
