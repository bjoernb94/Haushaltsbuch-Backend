package de.bochmann.haushaltsbuch.shared.security;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import de.bochmann.haushaltsbuch.shared.fehler.Probleme;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anmeldung über das eigene Login-Formular der SPA. Abmelden: {@code POST /api/abmeldung}
 * (siehe {@link SecurityConfig}).
 */
@RestController
@Tag(name = "Anmeldung")
@RequestMapping("/api/anmeldung")
class AnmeldungController {

	private final Anmeldung anmeldung;

	AnmeldungController(Anmeldung anmeldung) {
		this.anmeldung = anmeldung;
	}

	record AnmeldungAnfrage(@NotBlank String email, @NotBlank String passwort) {

		@Override
		public String toString() {
			// Passwort nicht versehentlich loggen.
			return "AnmeldungAnfrage[email=%s]".formatted(email);
		}

	}

	@PostMapping
	@SecurityRequirements // öffentlich
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void anmelden(@Valid @RequestBody AnmeldungAnfrage anfrage, HttpServletRequest request,
			HttpServletResponse response) {
		anmeldung.anmelden(anfrage.email(), anfrage.passwort(), request, response);
	}

	@ExceptionHandler
	ProblemDetail anmeldungFehlgeschlagen(AnmeldungFehlgeschlagenException e) {
		return switch (e.getGrund()) {
			case UNGUELTIG -> Probleme.problem(HttpStatus.UNAUTHORIZED, "ANMELDUNG_UNGUELTIG",
					"Invalid email or password.");
			case GESPERRT -> Probleme.problem(HttpStatus.FORBIDDEN, "ANMELDUNG_GESPERRT",
					"Account is disabled or temporarily locked.");
			case NICHT_EINGERICHTET -> Probleme.problem(HttpStatus.FORBIDDEN, "ANMELDUNG_NICHT_EINGERICHTET",
					"Account setup is not complete.");
		};
	}

}
