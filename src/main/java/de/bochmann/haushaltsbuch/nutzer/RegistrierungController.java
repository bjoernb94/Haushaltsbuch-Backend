package de.bochmann.haushaltsbuch.nutzer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Öffentlicher Endpunkt für das eigene Registrierungsformular der SPA. Nach erfolgreicher
 * Registrierung meldet sich der Nutzer über den normalen Login-Flow an
 * ({@code /oauth2/authorization/keycloak}).
 */
@RestController
@RequestMapping("/api/registrierung")
class RegistrierungController {

	private final RegistrierungService registrierungService;

	RegistrierungController(RegistrierungService registrierungService) {
		this.registrierungService = registrierungService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void registrieren(@Valid @RequestBody RegistrierungAnfrage anfrage) {
		registrierungService.registrieren(anfrage);
	}

	@ExceptionHandler
	ProblemDetail emailBereitsVergeben(EmailBereitsVergebenException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail registrierungAbgelehnt(RegistrierungAbgelehntException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

}
