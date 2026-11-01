package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.Komponente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Daten des Registrierungsformulars. Die Checkboxen des Formulars werden als Liste der
 * gewählten {@link Komponente}n übertragen.
 * <p>
 * Höchstlängen wie in Keycloak (Spalten mit 255 Zeichen), damit zu lange Werte als
 * {@code VALIDIERUNG} abgelehnt werden statt erst von Keycloak.
 */
record RegistrierungAnfrage(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 255) String passwort,
		@NotBlank @Size(max = 255) String vorname,
		@NotBlank @Size(max = 255) String name,
		@NotEmpty Set<Komponente> komponenten) {

	@Override
	public String toString() {
		// Passwort nicht versehentlich loggen.
		return "RegistrierungAnfrage[email=%s, vorname=%s, name=%s, komponenten=%s]"
				.formatted(email, vorname, name, komponenten);
	}

}
