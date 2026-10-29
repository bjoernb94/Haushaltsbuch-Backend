package de.bochmann.haushaltsbuch.nutzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ersatz für Keycloak in der lokalen Entwicklung ohne Profil {@code keycloak}: legt kein
 * Konto an, sondern vergibt nur eine zufällige ID. Ein Login ist damit nicht möglich.
 */
@Component
@Profile("!keycloak")
class LokalerIdentitaetsanbieter implements Identitaetsanbieter {

	private static final Logger log = LoggerFactory.getLogger(LokalerIdentitaetsanbieter.class);

	@Override
	public UUID benutzerAnlegen(String email, String passwort, String vorname, String name) {
		UUID id = UUID.randomUUID();
		log.warn("Keycloak-Profil nicht aktiv: Konto für {} wird nicht angelegt, ID {} vergeben.", email, id);
		return id;
	}

	@Override
	public void benutzerLoeschen(UUID id) {
		log.warn("Keycloak-Profil nicht aktiv: Konto {} wird nicht gelöscht.", id);
	}

}
