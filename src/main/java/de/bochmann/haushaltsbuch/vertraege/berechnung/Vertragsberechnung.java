package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Einmalkosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Kosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Kostenberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Laufzeitberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preispunkt;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeile;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Prognosepreis;

import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/**
 * Berechnete Angaben eines Vertrags zu einem Stichtag ({@code heute}): Status, Abbuchungen, Kosten im Jahr,
 * Preisänderung und Kündigung. Ohne Spring und ohne Uhr, damit Tests jeden Tag festlegen können; die Aufrufer geben
 * {@code LocalDate.now(clock)} mit.
 * <p>
 * Fassade über die Teilberechnungen: {@link #laufzeit()} (Status, Vertragsende, Kündigung), {@link #preise()}
 * (aktueller Preis, Preissteigerung, Prognose) und {@link #kosten()} (Abbuchungen und Kosten im Jahr). Sie hängen in
 * dieser Reihenfolge voneinander ab, nie umgekehrt.
 */
public class Vertragsberechnung {

	private final LocalDate heute;

	private final Laufzeitberechnung laufzeit;

	private final Preisberechnung preise;

	private final Kostenberechnung kosten;

	public Vertragsberechnung(LocalDate heute) {
		this.heute = heute;
		this.laufzeit = new Laufzeitberechnung(heute);
		this.preise = new Preisberechnung(laufzeit);
		this.kosten = new Kostenberechnung(laufzeit, preise);
	}

	/** Der Stichtag; sein Jahr ist das laufende Jahr für {@link Kostenberechnung#kostenImJahr}. */
	public LocalDate heute() {
		return heute;
	}

	public Laufzeitberechnung laufzeit() {
		return laufzeit;
	}

	public Preisberechnung preise() {
		return preise;
	}

	public Kostenberechnung kosten() {
		return kosten;
	}

	/**
	 * Alle berechneten Werte, für den gespeicherten Vertrag wie für die Vorschau (eine Berechnung für beide). Fehlt
	 * eine Angabe, bleiben die Werte leer, die sie brauchen; siehe {@link Vertragswerte}. Der Anteil wird auf die
	 * ungerundeten Werte angewendet (ADR 0011).
	 */
	public Vertragswerte werte(Berechnungsgrundlage grundlage) {
		var daten = grundlage.daten();
		var liste = grundlage.preise();
		var prognose = grundlage.prognose();
		var anteil = grundlage.anteil();
		Betrag betrag = null;
		boolean betragErrechnet = false;
		List<Preiszeile> preiszeilen = List.of();
		Betrag kostenProMonat = null;
		Betrag kostenImJahr = null;
		boolean mitPrognose = false;
		Betrag anteilBetrag = null;
		Betrag anteilProMonat = null;
		Betrag anteilImJahr = null;
		Preisaenderung naechstePreisaenderung = null;
		List<Prognosepreis> prognosepreise = List.of();
		List<Preispunkt> preisverlauf = List.of();
		if (liste != null) {
			Preis aktuell = preise.aktuellerEintrag(daten, liste, prognose);
			betrag = aktuell.betrag();
			betragErrechnet = aktuell.herkunft() == Preisherkunft.ERRECHNET;
			preiszeilen = preise.preiszeilen(daten, liste);
			anteilBetrag = anteil.geteilt() ? anteil.von(betrag).gerundet() : null;
			naechstePreisaenderung = preise.naechstePreisaenderung(daten, liste, prognose).orElse(null);
			prognosepreise = preise.prognose(daten, liste, prognose);
			preisverlauf = preise.preisverlauf(daten, liste, prognose);
			if (daten.zahlweise() != null) {
				Betrag proJahr = Kosten.proJahr(betrag, daten.zahlweise());
				kostenProMonat = Kosten.proMonat(proJahr);
				anteilProMonat = anteil.geteilt() ? Kosten.proMonat(anteil.von(proJahr)) : null;
				if (daten.beginn() != null) {
					kostenImJahr = kosten.kostenImJahr(daten, liste, prognose);
					mitPrognose = kosten.kostenImJahrMitPrognose(daten, liste, prognose);
					anteilImJahr = anteil.geteilt() ? anteil.von(kostenImJahr).gerundet() : null;
				}
			}
		}
		var einmalkosten = grundlage.einmalkosten();
		Currency waehrung = liste != null ? liste.waehrung()
				: einmalkosten.isEmpty() ? null : einmalkosten.getFirst().betrag().waehrung();
		return new Vertragswerte(laufzeit.status(daten), betrag, betragErrechnet, preiszeilen, kostenProMonat,
				kostenImJahr, mitPrognose, anteilBetrag, anteilProMonat, anteilImJahr,
				waehrung != null ? Einmalkosten.summe(einmalkosten, waehrung) : null,
				laufzeit.vertragsende(daten).orElse(null), daten.aktuellerBeginn(),
				laufzeit.wiederaufnahme(daten).orElse(null), naechstePreisaenderung, prognosepreise, preisverlauf,
				laufzeit.kuendigung(daten));
	}

	/**
	 * Die Grundlage, wie sie nach dem Speichern wäre: die Preise ergänzt um die
	 * {@link Preisberechnung#faelligeStufen fälligen Stufen}, die Termine bis {@link Preisberechnung#faelligBis}
	 * betrachtet. Für die Vorschau, damit sie zeigt, was das Speichern anlegt.
	 */
	public Berechnungsgrundlage wieGespeichert(Berechnungsgrundlage grundlage) {
		if (grundlage.preise() == null) {
			return grundlage;
		}
		var liste = grundlage.preise()
				.mit(preise.faelligeStufen(grundlage.daten(), grundlage.preise(), grundlage.prognose()));
		var prognose = grundlage.prognose().steigerung() == null ? grundlage.prognose()
				: grundlage.prognose().betrachtetBis(preise.faelligBis(grundlage.daten()));
		return new Berechnungsgrundlage(grundlage.daten(), liste, prognose, grundlage.anteil(),
				grundlage.einmalkosten());
	}

}
