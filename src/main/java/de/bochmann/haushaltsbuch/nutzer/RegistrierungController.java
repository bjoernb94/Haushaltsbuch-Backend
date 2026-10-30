package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.security.Anmeldung;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

	/** Nur mit Profil {@code keycloak} vorhanden; ohne gibt es keine Anmeldung. */
	private final ObjectProvider<Anmeldung> anmeldung;

	RegistrierungController(RegistrierungService registrierungService, ObjectProvider<Anmeldung> anmeldung) {
		this.registrierungService = registrierungService;
		this.anmeldung = anmeldung;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void registrieren(@Valid @RequestBody RegistrierungAnfrage anfrage, HttpServletRequest request,
			HttpServletResponse response) {
		registrierungService.registrieren(anfrage);
		anmeldung.ifAvailable(a -> {
			try {
				a.anmelden(anfrage.email(), anfrage.passwort(), request, response);
			}
			catch (RuntimeException e) {
				// Das Konto ist angelegt; die SPA merkt an der 401 von /api/nutzer/ich/komponenten,
				// dass sich der Nutzer selbst anmelden muss.
				log.warn("Registrierung von {} erfolgreich, automatische Anmeldung fehlgeschlagen.",
						anfrage.email(), e);
			}
		});
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
