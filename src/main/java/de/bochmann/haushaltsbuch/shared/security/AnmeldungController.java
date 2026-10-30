package de.bochmann.haushaltsbuch.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
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
@RequestMapping("/api/anmeldung")
@Profile("keycloak")
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
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void anmelden(@Valid @RequestBody AnmeldungAnfrage anfrage, HttpServletRequest request,
			HttpServletResponse response) {
		anmeldung.anmelden(anfrage.email(), anfrage.passwort(), request, response);
	}

	@ExceptionHandler
	ProblemDetail anmeldungFehlgeschlagen(AnmeldungFehlgeschlagenException e) {
		ProblemDetail problem = switch (e.getGrund()) {
			case UNGUELTIG -> ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
					"E-Mail-Adresse oder Passwort ist falsch.");
			case GESPERRT -> ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
					"Das Konto ist gesperrt.");
			case NICHT_EINGERICHTET -> ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
					"Das Konto ist noch nicht vollständig eingerichtet.");
		};
		// Für die SPA, damit sie nicht am Text unterscheiden muss.
		problem.setProperty("grund", e.getGrund());
		return problem;
	}

}
