package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preispunkt;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Prognosepreis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Wiederaufnahmetermin;
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
 * {@code preise}, bei einem beendeten Vertrag der an seinem letzten Tag gültige; der Eintrag hat {@code art}
 * {@code AKTUELL}) und wird beim Ändern ignoriert, ebenso {@code art} und {@code betragErrechnet}. Ist seit dem
 * letzten Speichern eine Stufe der Preissteigerung fällig geworden, ist {@code betrag} diese
 * ({@code betragErrechnet}); {@code preise} enthält nur Gespeichertes. {@code preissteigerungBetrachtetBis} schickt
 * die SPA unverändert an die Vorschau, damit sie gelöschte Stufen nicht wieder anlegt; beim Ändern wird er ignoriert.
 *
 * @param unterbrechungen sortiert nach {@code von}; die SPA schickt beim Ändern die IDs der verbleibenden als
 *        {@code unterbrechungIds}, an die Vorschau die verbleibenden selbst
 */
record VertragAntwort(
		UUID id,
		long version,
		String anbieter,
		@Nullable String bezeichnung,
		VertragKategorieAntwort kategorie,
		@Nullable String vertragsnummer,
		Betrag betrag,
		boolean betragErrechnet,
		List<VertragPreisAntwort> preise,
		Zahlweise zahlweise,
		@Nullable Zahlungsweg zahlungsweg,
		@Nullable BigDecimal anteilProzent,
		List<VertragEinmalkostenAntwort> einmalkosten,
		@Nullable VertragPreissteigerungAntwort preissteigerung,
		@Nullable LocalDate preisgarantieBis,
		@Nullable LocalDate preissteigerungBetrachtetBis,
		boolean erinnerungSonderkuendigung,
		boolean kuendbar,
		LocalDate beginn,
		@Nullable LocalDate laufzeitende,
		@Nullable Verlaengerung verlaengerung,
		@Nullable Integer kuendigungsfristMonate,
		@Nullable LocalDate gekuendigtZum,
		@Nullable String notiz,
		List<VertragUnterbrechungAntwort> unterbrechungen,
		VertragBerechnet berechnet) {

	/**
	 * Zum Stichtag berechnet, ändert sich also mit der Zeit.
	 *
	 * @param kostenProMonat {@code betrag} auf einen Monat umgerechnet
	 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr {@code jahr}, jede mit ihrem Preis; ab einem Termin
	 *        der Prognose mit dem prognostizierten
	 * @param kostenImJahrMitPrognose ob {@code kostenImJahr} einen prognostizierten Preis enthält („inkl. Prognose“)
	 * @param jahr das laufende Jahr laut Backend, für die Beschriftung „Kosten &lt;Jahr&gt;“
	 * @param eigenerAnteil der eigene Anteil an den Kosten; leer ohne Anteil unter 100&nbsp;% (dann sind die vollen
	 *        Kosten der eigene Anteil)
	 * @param einmalkosten Summe aller Einmalkosten, unabhängig vom Jahr und mit dem vollen Betrag; ohne Einträge 0
	 * @param vertragsende der letzte Vertragstag ({@code gekuendigtZum} bzw. Laufzeitende ohne Verlängerung, das
	 *        frühere); leer bei offenem Ende
	 * @param aktuellerBeginn Beginn der letzten Phase: nach einer Unterbrechung der Tag der Wiederaufnahme, sonst
	 *        {@code beginn}; ab ihm zählen Laufzeit und Verlängerung
	 * @param wiederaufnahme wann sich der Vertrag wieder aufnehmen lässt; leer, wenn nicht (nur gekündigt oder beendet)
	 * @param naechstePreisaenderung der nächste Eintrag nach dem heute gültigen, sonst der erste der Prognose; leer,
	 *        wenn keiner bekannt ist
	 * @param prognose die nächsten drei Preise aus der Preissteigerung; leer ohne Regel
	 * @param preisverlauf die Punkte für das Diagramm, sortiert
	 */
	record VertragBerechnet(
			Vertragsstatus status,
			Betrag kostenProMonat,
			Betrag kostenImJahr,
			boolean kostenImJahrMitPrognose,
			int jahr,
			@Nullable VertragEigenerAnteil eigenerAnteil,
			Betrag einmalkosten,
			@Nullable LocalDate vertragsende,
			LocalDate aktuellerBeginn,
			@Nullable Wiederaufnahmetermin wiederaufnahme,
			@Nullable Preisaenderung naechstePreisaenderung,
			List<Prognosepreis> prognose,
			List<Preispunkt> preisverlauf,
			Kuendigung kuendigung) {
	}

	/**
	 * Der eigene Anteil an {@code betrag} (je Abbuchung), {@code kostenProMonat} und {@code kostenImJahr}, jeder aus
	 * dem ungerundeten Wert gerechnet und dann gerundet (ADR 0011).
	 */
	record VertragEigenerAnteil(Betrag betrag, Betrag kostenProMonat, Betrag kostenImJahr) {
	}

	/** {@code betrag} siehe {@link Vertragsberechnung#aktuellerPreis}. */
	static VertragAntwort aus(Vertrag vertrag, Vertragsberechnung berechnung) {
		// Gespeichert ist alles vollständig, die Werte also gesetzt, wo die API keine null kennt.
		var werte = berechnung.werte(vertrag.grundlage());
		var berechnet = new VertragBerechnet(
				werte.status(),
				werte.kostenProMonat(),
				werte.kostenImJahr(),
				werte.kostenImJahrMitPrognose(),
				berechnung.heute().getYear(),
				werte.eigenerAnteilBetrag() != null
						? new VertragEigenerAnteil(werte.eigenerAnteilBetrag(), werte.eigenerAnteilProMonat(),
								werte.eigenerAnteilImJahr())
						: null,
				werte.einmalkosten(),
				werte.vertragsende(),
				werte.aktuellerBeginn(),
				werte.wiederaufnahme(),
				werte.naechstePreisaenderung(),
				werte.prognose(),
				werte.preisverlauf(),
				werte.kuendigung());
		return new VertragAntwort(vertrag.getId(), vertrag.getVersion(), vertrag.getAnbieter(),
				vertrag.getBezeichnung(), VertragKategorieAntwort.aus(vertrag.getKategorie()), vertrag.getVertragsnummer(),
				werte.betrag(), werte.betragErrechnet(), werte.preise().stream().map(VertragPreisAntwort::aus).toList(), vertrag.getZahlweise(), vertrag.getZahlungsweg(),
				vertrag.getAnteilProzent(),
				vertrag.getEinmalkosten().stream().map(VertragEinmalkostenAntwort::aus).toList(),
				VertragPreissteigerungAntwort.aus(vertrag.getPreissteigerung()), vertrag.getPreisgarantieBis(), vertrag.getPreissteigerungBetrachtetBis(),
				vertrag.isErinnerungSonderkuendigung(), vertrag.isKuendbar(), vertrag.getBeginn(), vertrag.getLaufzeitende(), vertrag.getVerlaengerung(),
				vertrag.getKuendigungsfristMonate(), vertrag.getGekuendigtZum(), vertrag.getNotiz(),
				vertrag.getUnterbrechungen().stream().map(VertragUnterbrechungAntwort::aus).toList(), berechnet);
	}

}
