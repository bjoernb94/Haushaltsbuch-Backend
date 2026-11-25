package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.fehler.KeycloakNichtErreichbarException;

import java.util.UUID;

/**
 * Verwaltung der Benutzerkonten im Identity Provider (Keycloak). Dort liegen E-Mail,
 * Name und Passwort; unsere Datenbank kennt nur die zurückgegebene ID.
 */
interface Identitaetsanbieter {

	/**
	 * Legt ein Benutzerkonto an.
	 *
	 * @return die ID des Kontos, entspricht später dem Claim {@code sub}
	 * @throws EmailBereitsVergebenException wenn bereits ein Konto mit dieser E-Mail existiert
	 * @throws RegistrierungAbgelehntException wenn der Identity Provider die Daten ablehnt,
	 *         z.&nbsp;B. wegen der Passwort-Richtlinie
	 * @throws KeycloakNichtErreichbarException wenn der Identity Provider nicht rechtzeitig antwortet;
	 *         das Konto kann dann trotzdem angelegt worden sein (#179)
	 */
	UUID benutzerAnlegen(String email, String passwort, String vorname, String name);

	/**
	 * @throws KeycloakNichtErreichbarException wenn der Identity Provider nicht rechtzeitig antwortet
	 */
	void benutzerLoeschen(UUID id);

}
