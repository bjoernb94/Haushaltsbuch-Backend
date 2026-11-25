package de.bochmann.haushaltsbuch.shared.fehler;

import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Keycloak antwortet nicht (Timeout, Verbindung abgelehnt, Netzwerkfehler): 503 mit Code
 * {@value Probleme#KEYCLOAK_NICHT_ERREICHBAR}. Die Timeouts stehen unter {@code spring.http.clients.*}.
 * <p>
 * Entsteht in der Regel über {@link #beiAufruf(Supplier)}: Die Bibliotheken melden den Ausfall je nach Weg
 * unterschiedlich verpackt (z.&nbsp;B. {@code ResourceAccessException} vom {@code RestClient},
 * {@code JwtException} beim Laden der Schlüssel, {@code OAuth2AuthorizationException} beim Token des
 * Service Accounts), gemeinsam ist ihnen ein Ein-/Ausgabefehler als Ursache.
 */
public class KeycloakNichtErreichbarException extends RuntimeException {

	public KeycloakNichtErreichbarException(Throwable ursache) {
		super("Keycloak is not reachable.", ursache);
	}

	/**
	 * Führt einen Aufruf an Keycloak aus. Scheitert er an einem Ein-/Ausgabefehler, wird daraus diese
	 * Exception; alle anderen Fehler (z.&nbsp;B. abgelehnte Anmeldedaten) kommen unverändert durch.
	 */
	public static <T> T beiAufruf(Supplier<T> aufruf) {
		try {
			return aufruf.get();
		}
		catch (RuntimeException e) {
			if (nichtErreichbar(e)) {
				throw new KeycloakNichtErreichbarException(e);
			}
			throw e;
		}
	}

	/** Wie {@link #beiAufruf(Supplier)}, für Aufrufe ohne Ergebnis. */
	public static void beiAufruf(Runnable aufruf) {
		beiAufruf(() -> {
			aufruf.run();
			return null;
		});
	}

	private static boolean nichtErreichbar(Throwable fehler) {
		for (Throwable t = fehler; t != null; t = t.getCause()) {
			if (t instanceof ResourceAccessException || t instanceof IOException) {
				return true;
			}
		}
		return false;
	}

}
