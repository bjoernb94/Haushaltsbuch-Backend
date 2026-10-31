package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.fehler.Probleme;
import de.bochmann.haushaltsbuch.shared.security.Anmeldung;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Registrierung ist der Nutzer direkt angemeldet (Session-Cookie in der Antwort).
 */
@RestController
@RequestMapping("/api/registrierung")
class RegistrierungController {

	private static final Logger log = LoggerFactory.getLogger(RegistrierungController.class);

	private final RegistrierungService registrierungService;

	private final Anmeldung anmeldung;

	RegistrierungController(RegistrierungService registrierungService, Anmeldung anmeldung) {
		this.registrierungService = registrierungService;
		this.anmeldung = anmeldung;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void registrieren(@Valid @RequestBody RegistrierungAnfrage anfrage, HttpServletRequest request,
			HttpServletResponse response) {
		registrierungService.registrieren(anfrage);
		try {
			anmeldung.anmelden(anfrage.email(), anfrage.passwort(), request, response);
		}
		catch (RuntimeException e) {
			// Das Konto ist angelegt; die SPA merkt an der 401 von /api/nutzer/ich/komponenten,
			// dass sich der Nutzer selbst anmelden muss.
			log.warn("Registrierung von {} erfolgreich, automatische Anmeldung fehlgeschlagen.",
					anfrage.email(), e);
		}
	}

	@ExceptionHandler
	ProblemDetail emailBereitsVergeben(EmailBereitsVergebenException e) {
		return Probleme.problem(HttpStatus.CONFLICT, "EMAIL_BEREITS_VERGEBEN", e.getMessage());
	}

	@ExceptionHandler
	ProblemDetail registrierungAbgelehnt(RegistrierungAbgelehntException e) {
		return Probleme.problem(HttpStatus.BAD_REQUEST, "REGISTRIERUNG_ABGELEHNT", e.getMessage());
	}

}
