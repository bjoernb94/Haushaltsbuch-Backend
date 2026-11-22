package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeile;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart;

import java.time.LocalDate;
import java.util.Set;

/**
 * Ein Eintrag der Preisliste in der Vorschau, wie {@link VertragPreisAntwort}.
 *
 * @param automatisch bei einer Zeile der Anfrage wie dort angegeben (ungeprüft), bei einer neuen {@code true}
 * @param neu eine fällige Stufe der Preissteigerung, die erst das Speichern anlegt; die SPA schickt sie nicht mit
 */
record VertragVorschauPreisAntwort(LocalDate gueltigAb, Betrag betrag, Preiszeilenart art, boolean automatisch,
		boolean neu) {

	/** @param angefragt {@code gueltigAb} der Zeilen aus der Anfrage; die übrigen sind neu */
	static VertragVorschauPreisAntwort aus(Preiszeile preis, Set<LocalDate> angefragt) {
		return new VertragVorschauPreisAntwort(preis.gueltigAb(), preis.betrag(), preis.art(), preis.automatisch(),
				!angefragt.contains(preis.gueltigAb()));
	}

}
