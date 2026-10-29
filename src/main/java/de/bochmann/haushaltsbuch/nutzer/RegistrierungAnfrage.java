package de.bochmann.haushaltsbuch.nutzer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Daten des Registrierungsformulars. Die Checkboxen des Formulars werden als Liste der
 * gewählten {@link Komponente}n übertragen.
 */
record RegistrierungAnfrage(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8) String passwort,
		@NotBlank String vorname,
		@NotBlank String name,
		@NotEmpty Set<Komponente> komponenten) {

	@Override
	public String toString() {
		// Passwort nicht versehentlich loggen.
		return "RegistrierungAnfrage[email=%s, vorname=%s, name=%s, komponenten=%s]"
				.formatted(email, vorname, name, komponenten);
	}

}
