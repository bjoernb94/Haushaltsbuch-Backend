package de.bochmann.haushaltsbuch.shared.fehler;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fehlerantworten der API ({@link ProblemDetail}, {@code application/problem+json}).
 * <p>
 * Jede Antwort trägt in {@value #CODE} einen stabilen Code (z.&nbsp;B. {@code NICHT_GEFUNDEN}), an dem
 * die SPA den Fehler erkennt und den Text in der Sprache des Nutzers anzeigt. {@code detail} ist
 * englisch und nur für Entwickler und Logs gedacht, nicht für die Oberfläche. Module verwenden für
 * ihre eigenen Fehler eigene Codes (z.&nbsp;B. {@code EMAIL_BEREITS_VERGEBEN}).
 */
public final class Probleme {

	/** Name der Property mit dem Fehlercode. */
	public static final String CODE = "code";

	/** Request-Body mit ungültigen Feldern; die Felder stehen in {@code felder}. */
	public static final String VALIDIERUNG = "VALIDIERUNG";

	/** Request nicht lesbar oder unpassend (z.&nbsp;B. kaputtes JSON, unbekannter Enum-Wert). */
	public static final String ANFRAGE_UNGUELTIG = "ANFRAGE_UNGUELTIG";

	public static final String NICHT_ANGEMELDET = "NICHT_ANGEMELDET";

	/** Fehlende Berechtigung, z.&nbsp;B. Komponente nicht gebucht. */
	public static final String KEIN_ZUGRIFF = "KEIN_ZUGRIFF";

	public static final String CSRF_TOKEN_UNGUELTIG = "CSRF_TOKEN_UNGUELTIG";

	/** Auch für Daten eines fremden Haushalts, siehe {@link NichtGefundenException}. */
	public static final String NICHT_GEFUNDEN = "NICHT_GEFUNDEN";

	public static final String METHODE_NICHT_ERLAUBT = "METHODE_NICHT_ERLAUBT";

	/** Gleichzeitige Änderung: Version veraltet, siehe {@link KonfliktException}. */
	public static final String KONFLIKT = "KONFLIKT";

	public static final String INTERNER_FEHLER = "INTERNER_FEHLER";

	private Probleme() {
	}

	public static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code);
		return problem;
	}

	/** Code für Fehler, die Spring ohne eigenen Code meldet (z.&nbsp;B. unbekannter Pfad). */
	static String standardcode(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return INTERNER_FEHLER;
		}
		HttpStatus bekannt = HttpStatus.resolve(status.value());
		return switch (bekannt == null ? HttpStatus.BAD_REQUEST : bekannt) {
			case UNAUTHORIZED -> NICHT_ANGEMELDET;
			case FORBIDDEN -> KEIN_ZUGRIFF;
			case NOT_FOUND -> NICHT_GEFUNDEN;
			case METHOD_NOT_ALLOWED -> METHODE_NICHT_ERLAUBT;
			case CONFLICT -> KONFLIKT;
			default -> ANFRAGE_UNGUELTIG;
		};
	}

}
