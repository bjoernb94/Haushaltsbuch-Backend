package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.shared.security.AngemeldeterNutzer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Der Haushalt, dessen Fachdaten gerade gelesen und geschrieben werden. Hibernate filtert alle
 * {@link FachEntity}s danach ({@code @TenantId}, siehe {@link HaushaltTenantResolver}).
 * <ul>
 *   <li><b>Im Request:</b> der Haushalt des angemeldeten Nutzers, einmal je Request ermittelt.</li>
 *   <li><b>Ohne Request</b> (z.&nbsp;B. Listener, Jobs): ausdrücklich mit {@link #fuer(UUID, Supplier)};
 *   die Transaktion muss darin beginnen, weil Hibernate den Haushalt beim Öffnen der Session liest.</li>
 * </ul>
 * Ohne Haushalt gilt {@link #KEIN_HAUSHALT}: Abfragen auf Fachdaten liefern dann nichts, Speichern
 * schlägt fehl. Das betrifft anonyme Requests (auch die Registrierung, die den Haushalt erst anlegt),
 * Code ohne Request und ohne {@link #fuer(UUID, Supplier)}. Registrierte Nutzer haben immer einen
 * Haushalt; nur Keycloak-Konten ohne Eintrag in {@code haushalt_mitglied} (z.&nbsp;B. direkt in
 * Keycloak angelegt) fallen zur Sicherheit ebenfalls darunter.
 */
@Component
public class HaushaltKontext {

	/** Platzhalter, weil Hibernate für jede Session einen Haushalt verlangt. */
	public static final UUID KEIN_HAUSHALT = new UUID(0, 0);

	private static final String ATTRIBUT = HaushaltKontext.class.getName();

	private static final ThreadLocal<UUID> AUSDRUECKLICH = new ThreadLocal<>();

	private final HaushaltAbfrage haushaltAbfrage;

	HaushaltKontext(HaushaltAbfrage haushaltAbfrage) {
		this.haushaltAbfrage = haushaltAbfrage;
	}

	/**
	 * Führt {@code arbeit} für den angegebenen Haushalt aus, z.&nbsp;B. in einem Listener auf
	 * {@code HaushaltGeloescht}. Transaktionen müssen innerhalb von {@code arbeit} beginnen.
	 */
	public static <T> T fuer(UUID haushaltId, Supplier<T> arbeit) {
		UUID vorher = AUSDRUECKLICH.get();
		AUSDRUECKLICH.set(haushaltId);
		try {
			return arbeit.get();
		}
		finally {
			if (vorher == null) {
				AUSDRUECKLICH.remove();
			}
			else {
				AUSDRUECKLICH.set(vorher);
			}
		}
	}

	/** @return der aktuelle Haushalt, leer wenn es keinen gibt */
	public Optional<UUID> id() {
		UUID ausdruecklich = AUSDRUECKLICH.get();
		if (ausdruecklich != null) {
			return Optional.of(ausdruecklich);
		}
		Optional<UUID> nutzerId = AngemeldeterNutzer.id(SecurityContextHolder.getContext().getAuthentication());
		if (nutzerId.isEmpty()) {
			return Optional.empty();
		}
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		if (request == null) {
			return haushaltAbfrage.haushaltVon(nutzerId.get());
		}
		// Einmal je Request und Nutzer: Hibernate fragt bei jeder Session (je Transaktion) erneut.
		// Je Nutzer, weil sich die Anmeldung im Request ändern kann (Registrierung, Anmeldung).
		String attribut = ATTRIBUT + "." + nutzerId.get();
		@SuppressWarnings("unchecked")
		var gemerkt = (Optional<UUID>) request.getAttribute(attribut, RequestAttributes.SCOPE_REQUEST);
		if (gemerkt == null) {
			gemerkt = haushaltAbfrage.haushaltVon(nutzerId.get());
			request.setAttribute(attribut, gemerkt, RequestAttributes.SCOPE_REQUEST);
		}
		return gemerkt;
	}

}
