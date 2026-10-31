package de.bochmann.haushaltsbuch.shared.fachdaten;

import java.util.Optional;
import java.util.UUID;

/**
 * Liefert den Haushalt eines Nutzers für den {@link HaushaltKontext}. Wird vom Modul
 * {@code haushalt} implementiert, damit {@code shared} nicht von diesem Modul abhängt.
 * <p>
 * Die Implementierung darf nicht über JPA lesen: Sie läuft, während Hibernate eine Session öffnet
 * und dafür den Haushalt ermittelt.
 */
public interface HaushaltAbfrage {

	/**
	 * @param nutzerId Benutzer-ID aus Keycloak (Claim {@code sub})
	 * @return die ID des Haushalts, leer wenn der Nutzer keinem angehört
	 */
	Optional<UUID> haushaltVon(UUID nutzerId);

}
