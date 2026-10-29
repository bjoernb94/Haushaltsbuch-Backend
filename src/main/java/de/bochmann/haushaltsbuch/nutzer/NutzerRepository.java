package de.bochmann.haushaltsbuch.nutzer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface NutzerRepository extends JpaRepository<Nutzer, UUID> {
}
