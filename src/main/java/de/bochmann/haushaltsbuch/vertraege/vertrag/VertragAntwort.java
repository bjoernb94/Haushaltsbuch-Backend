package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Abbuchung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieAntwort;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ein Vertrag mit den gespeicherten Angaben (wie in {@link VertragAnfrage}, Kategorie als Objekt) und
 * den berechneten in {@code berechnet}. Die gespeicherten Angaben schickt die SPA beim Ändern
 * unverändert zurück; {@code berechnet} nie. {@code betrag} ist berechnet (der heute gültige Preis aus
 * {@code preise}) und wird beim Ändern ignoriert.
 */
record VertragAntwort(
		UUID id,
		long version,
		String anbieter,
		@Nullable String bezeichnung,
		VertragKategorieAntwort kategorie,
		@Nullable String vertragsnummer,
		Betrag betrag,
		List<VertragPreisAntwort> preise,
		Zahlweise zahlweise,
		LocalDate abbuchungAb,
		@Nullable Zahlungsweg zahlungsweg,
		@Nullable BigDecimal anteilProzent,
		boolean kuendbar,
		@Nullable LocalDate beginn,
		@Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Nullable Integer kuendigungsfristMonate,
		@Nullable LocalDate gekuendigtZum,
		@Nullable String notiz,
		VertragBerechnet berechnet) {

	/**
	 * Zum Stichtag berechnet, ändert sich also mit der Zeit.
	 *
	 * @param kostenProMonat der heute gültige Betrag auf einen Monat umgerechnet
	 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr {@code jahr}, jede mit ihrem Preis
	 * @param jahr das laufende Jahr laut Backend, für die Beschriftung „Kosten &lt;Jahr&gt;“
	 * @param eigenerAnteil der eigene Anteil an den Kosten; leer ohne Anteil unter 100&nbsp;% (dann sind die vollen
	 *        Kosten der eigene Anteil)
	 * @param naechsteAbbuchung die erste Abbuchung ab heute; leer, wenn keine mehr kommt
	 * @param naechstePreisaenderung der nächste Eintrag nach dem heute gültigen; leer, wenn keiner bekannt ist
	 */
	record VertragBerechnet(
			Vertragsstatus status,
			Betrag kostenProMonat,
			Betrag kostenImJahr,
			int jahr,
			@Nullable VertragEigenerAnteil eigenerAnteil,
			@Nullable Abbuchung naechsteAbbuchung,
			@Nullable Preisaenderung naechstePreisaenderung,
			Kuendigung kuendigung) {
	}

	/**
	 * Der eigene Anteil an {@code kostenProMonat} und {@code kostenImJahr}, aus den ungerundeten Werten gerechnet
	 * und dann gerundet (ADR 0011).
	 */
	record VertragEigenerAnteil(Betrag kostenProMonat, Betrag kostenImJahr) {
	}

	/** {@code betrag} ist der heute gültige Preis; beginnt der Vertrag erst in der Zukunft, der erste. */
	static VertragAntwort aus(Vertrag vertrag, Vertragsberechnung berechnung) {
		var daten = vertrag.daten();
		var preise = vertrag.preisliste();
		Betrag betrag = preise.am(berechnung.heute());
		Betrag proJahr = Kosten.proJahr(betrag, vertrag.getZahlweise());
		Betrag kostenImJahr = berechnung.kostenImJahr(daten, preise);
		var anteil = vertrag.anteil();
		var eigenerAnteil = anteil.geteilt()
				? new VertragEigenerAnteil(Kosten.proMonat(anteil.von(proJahr)), anteil.von(kostenImJahr).gerundet())
				: null;
		var berechnet = new VertragBerechnet(
				berechnung.status(daten),
				Kosten.proMonat(proJahr),
				kostenImJahr,
				berechnung.heute().getYear(),
				eigenerAnteil,
				berechnung.naechsteAbbuchung(daten, preise).orElse(null),
				berechnung.naechstePreisaenderung(daten, preise).orElse(null),
				berechnung.kuendigung(daten));
		return new VertragAntwort(vertrag.getId(), vertrag.getVersion(), vertrag.getAnbieter(),
				vertrag.getBezeichnung(), VertragKategorieAntwort.aus(vertrag.getKategorie()), vertrag.getVertragsnummer(),
				betrag, vertrag.getPreise().stream().map(VertragPreisAntwort::aus).toList(), vertrag.getZahlweise(), vertrag.getAbbuchungAb(), vertrag.getZahlungsweg(),
				vertrag.getAnteilProzent(), vertrag.isKuendbar(), vertrag.getBeginn(), vertrag.getLaufzeitende(), vertrag.getVerlaengerung(),
				vertrag.getKuendigungsfristMonate(), vertrag.getGekuendigtZum(), vertrag.getNotiz(), berechnet);
	}

}
