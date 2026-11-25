package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;

import java.time.LocalDate;
import java.util.Map;

/**
 * Die Regeln der Wiederaufnahme (ADR 0023), die den gespeicherten Vertrag brauchen und daher kein Constraint an der
 * Anfrage sein können; die Codes stehen in {@code docs/api.md}.
 */
public final class Wiederaufnahmeregeln {

	private Wiederaufnahmeregeln() {
	}

	/**
	 * Prüft in der Reihenfolge der Felder im Dialog.
	 *
	 * @param kuendbar wie am gespeicherten Vertrag
	 * @param preise die gespeicherten Preise
	 * @param vertragsende der letzte Tag vor der Wiederaufnahme
	 * @throws UngueltigeEingabeException beim ersten Verstoß
	 */
	public static void pruefe(Wiederaufnahmeangabe wiederaufnahme, boolean kuendbar, Preisliste preise,
			LocalDate vertragsende) {
		LocalDate datum = wiederaufnahme.datum();
		if (!datum.isAfter(vertragsende)) {
			throw new UngueltigeEingabeException("datum", "NachVertragsende", Map.of("vertragsende", vertragsende),
					"Resumption %s must be after the end of the contract (%s).".formatted(datum, vertragsende));
		}
		if (kuendbar) {
			if (wiederaufnahme.verlaengerung() == null) {
				throw new UngueltigeEingabeException("verlaengerung", "NotNull",
						"A cancellable contract needs a renewal.");
			}
			if (wiederaufnahme.verlaengerung() == Verlaengerung.KEINE && wiederaufnahme.laufzeitende() == null) {
				throw new UngueltigeEingabeException("verlaengerung", "LaufzeitendeBeiEnde",
						"Without renewal the contract needs an end of term.");
			}
		}
		if (wiederaufnahme.laufzeitende() != null && wiederaufnahme.laufzeitende().isBefore(datum)) {
			throw new UngueltigeEingabeException("laufzeitende", "LaufzeitendeNachBeginn",
					"End of term %s is before the resumption %s.".formatted(wiederaufnahme.laufzeitende(), datum));
		}
		var betrag = wiederaufnahme.betrag();
		if (betrag != null) {
			var waehrung = preise.waehrung();
			if (!betrag.waehrung().equals(waehrung)) {
				throw new UngueltigeEingabeException("betrag.waehrung", "GleicheWaehrung",
						"Currency %s differs from the prices (%s).".formatted(betrag.waehrung(), waehrung));
			}
			// Ein automatischer Preis an dem Tag wird ersetzt, ein eingetragener nicht.
			if (preise.preise().stream().anyMatch(preis -> preis.gueltigAb().equals(datum)
					&& preis.herkunft() == Preisherkunft.EINGETRAGEN)) {
				throw new UngueltigeEingabeException("betrag", "GueltigAbEindeutig",
						"There is already a price on %s.".formatted(datum));
			}
		}
	}

}
