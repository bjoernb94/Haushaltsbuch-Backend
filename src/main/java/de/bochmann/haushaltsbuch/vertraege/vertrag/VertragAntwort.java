package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieAntwort;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ein Vertrag mit den gespeicherten Angaben (wie in {@link VertragAnfrage}, Kategorie als Objekt) und
 * den berechneten in {@code berechnet}. Die gespeicherten Angaben schickt die SPA beim Ändern
 * unverändert zurück; {@code berechnet} nie.
 */
record VertragAntwort(
		UUID id,
		long version,
		String anbieter,
		String bezeichnung,
		VertragKategorieAntwort kategorie,
		String vertragsnummer,
		Betrag betrag,
		Zahlweise zahlweise,
		LocalDate abbuchungAb,
		Zahlungsweg zahlungsweg,
		boolean kuendbar,
		LocalDate beginn,
		LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		Integer kuendigungsfristMonate,
		LocalDate gekuendigtZum,
		String notiz,
		VertragBerechnet berechnet) {

	/**
	 * Zum Stichtag berechnet, ändert sich also mit der Zeit.
	 *
	 * @param naechsteAbbuchung die erste Abbuchung ab heute; leer, wenn keine mehr kommt
	 */
	record VertragBerechnet(
			Vertragsstatus status,
			Betrag kostenProMonat,
			Betrag kostenProJahr,
			LocalDate naechsteAbbuchung,
			Kuendigung kuendigung) {
	}

	static VertragAntwort aus(Vertrag vertrag, Vertragsberechnung berechnung) {
		var daten = vertrag.daten();
		var berechnet = new VertragBerechnet(
				berechnung.status(daten),
				Kosten.proMonat(vertrag.getBetrag(), vertrag.getZahlweise()),
				Kosten.proJahr(vertrag.getBetrag(), vertrag.getZahlweise()),
				berechnung.naechsteAbbuchung(daten).orElse(null),
				berechnung.kuendigung(daten));
		return new VertragAntwort(vertrag.getId(), vertrag.getVersion(), vertrag.getAnbieter(),
				vertrag.getBezeichnung(), VertragKategorieAntwort.aus(vertrag.getKategorie()), vertrag.getVertragsnummer(),
				vertrag.getBetrag(), vertrag.getZahlweise(), vertrag.getAbbuchungAb(), vertrag.getZahlungsweg(),
				vertrag.isKuendbar(), vertrag.getBeginn(), vertrag.getLaufzeitende(), vertrag.getVerlaengerung(),
				vertrag.getKuendigungsfristMonate(), vertrag.getGekuendigtZum(), vertrag.getNotiz(), berechnet);
	}

}
