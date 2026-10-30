package de.bochmann.haushaltsbuch.shared.security;

import de.bochmann.haushaltsbuch.shared.Komponente;

import java.util.Set;
import java.util.UUID;

/**
 * Liefert die gebuchten Komponenten eines Nutzers. Wird vom Modul {@code nutzer}
 * implementiert, damit die Security-Konfiguration nicht von diesem Modul abhängt.
 */
public interface KomponentenAbfrage {

	/**
	 * @param nutzerId Benutzer-ID aus Keycloak (Claim {@code sub})
	 * @return die gebuchten Komponenten, leer für unbekannte Nutzer
	 */
	Set<Komponente> komponentenVon(UUID nutzerId);

}
