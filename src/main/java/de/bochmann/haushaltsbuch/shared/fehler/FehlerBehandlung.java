package de.bochmann.haushaltsbuch.shared.fehler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Zentrale Fehlerbehandlung: beantwortet allgemeine Fehler aller Module als {@link ProblemDetail}
 * mit Code (siehe {@link Probleme}).
 * <p>
 * Als {@link ResponseEntityExceptionHandler} deckt sie die Standardfehler von Spring MVC ab
 * (ungültiger Body, kaputtes JSON, unbekannter Pfad, falsche Methode …); deshalb ist
 * {@code spring.mvc.problemdetails.enabled} nicht gesetzt. 401 und 403 der Security-Filterkette
 * leitet {@code SecurityConfig} hierher weiter. Modulspezifische Fehler beantworten die Controller
 * des Moduls selbst; deren {@code @ExceptionHandler} haben Vorrang.
 */
@RestControllerAdvice
class FehlerBehandlung extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(FehlerBehandlung.class);

	/** Attribute jeder Constraint-Annotation, die für die Anzeige keine Rolle spielen. */
	private static final Set<String> KEINE_PARAMETER = Set.of("message", "groups", "payload");

	/**
	 * Ungültiger Request-Body ({@code @Valid}): je Verstoß Feld, Constraint (z.&nbsp;B. {@code Size})
	 * und die gesetzten Attribute der Annotation (z.&nbsp;B. {@code min}), aus denen die SPA den Text
	 * bildet. Ein Feld kann mehrere Einträge haben.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Request contains invalid fields.");
		problem.setProperty(Probleme.CODE, Probleme.VALIDIERUNG);
		problem.setProperty("felder", ex.getBindingResult().getAllErrors().stream()
				.map(FehlerBehandlung::feldfehler)
				.toList());
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/** Fachliche Prüfung im Service, im selben Format wie {@code @Valid}. */
	@ExceptionHandler
	ProblemDetail ungueltigeEingabe(UngueltigeEingabeException e) {
		ProblemDetail problem = Probleme.problem(HttpStatus.BAD_REQUEST, Probleme.VALIDIERUNG, e.getMessage());
		problem.setProperty("felder", List.of(new Feldfehler(e.getFeld(), e.getCode(), e.getParameter())));
		return problem;
	}

	/**
	 * Ergänzt den Code bei Fehlern, die Spring ohne eigenen Code meldet. Den {@link ProblemDetail}
	 * legt oft erst {@code super} an (dann ist {@code body} hier noch {@code null}).
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> antwort = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (antwort != null && antwort.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(Probleme.CODE))) {
			problem.setProperty(Probleme.CODE, Probleme.standardcode(statusCode));
		}
		return antwort;
	}

	@ExceptionHandler
	ProblemDetail nichtAngemeldet(AuthenticationException e) {
		return Probleme.problem(HttpStatus.UNAUTHORIZED, Probleme.NICHT_ANGEMELDET, "Authentication required.");
	}

	@ExceptionHandler
	ProblemDetail keinZugriff(AccessDeniedException e) {
		if (e instanceof CsrfException) {
			return Probleme.problem(HttpStatus.FORBIDDEN, Probleme.CSRF_TOKEN_UNGUELTIG,
					"Invalid or missing CSRF token.");
		}
		return Probleme.problem(HttpStatus.FORBIDDEN, Probleme.KEIN_ZUGRIFF, "Access denied.");
	}

	/**
	 * Zwei Änderungen am selben Datensatz zur gleichen Zeit; die zweite scheitert beim Speichern an
	 * {@code @Version}. Veraltete Versionen vom Client meldet schon {@link KonfliktException}.
	 */
	@ExceptionHandler
	ProblemDetail konflikt(ObjectOptimisticLockingFailureException e) {
		return Probleme.problem(HttpStatus.CONFLICT, Probleme.KONFLIKT, "The data was modified in the meantime.");
	}

	/** Unerwartete Fehler: ohne Interna antworten, Details nur ins Log. */
	@ExceptionHandler
	ProblemDetail unerwartet(Exception e, HttpServletRequest request) {
		log.error("Unerwarteter Fehler bei {} {}", request.getMethod(), request.getRequestURI(), e);
		return Probleme.problem(HttpStatus.INTERNAL_SERVER_ERROR, Probleme.INTERNER_FEHLER,
				"An unexpected error occurred.");
	}

	/**
	 * Ein Verstoß; {@code feld} ist bei Constraints auf der ganzen Klasse {@code null}, außer der
	 * Validator hängt den Verstoß an ein Feld ({@code addPropertyNode}).
	 */
	record Feldfehler(String feld, String code, Map<String, Object> parameter) {
	}

	private static Feldfehler feldfehler(ObjectError fehler) {
		String feld = fehler instanceof FieldError feldFehler ? feldFehler.getField() : null;
		Map<String, Object> parameter = fehler.contains(ConstraintViolation.class)
				? parameter(fehler.unwrap(ConstraintViolation.class).getConstraintDescriptor())
				: Map.of();
		return new Feldfehler(feld, fehler.getCode(), parameter);
	}

	/** Nur Attribute, die von ihrem Standardwert abweichen, z.&nbsp;B. {@code min} bei {@code @Size(min = 8)}. */
	private static Map<String, Object> parameter(ConstraintDescriptor<?> constraint) {
		Map<String, Object> parameter = new TreeMap<>();
		constraint.getAttributes().forEach((name, wert) -> {
			if (!KEINE_PARAMETER.contains(name)
					&& !Objects.deepEquals(wert, standardwert(constraint.getAnnotation(), name))) {
				parameter.put(name, wert);
			}
		});
		return parameter;
	}

	private static Object standardwert(Annotation annotation, String attribut) {
		try {
			return annotation.annotationType().getMethod(attribut).getDefaultValue();
		}
		catch (NoSuchMethodException e) {
			return null;
		}
	}

}
