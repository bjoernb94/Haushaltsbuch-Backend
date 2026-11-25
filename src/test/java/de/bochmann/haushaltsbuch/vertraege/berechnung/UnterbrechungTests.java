package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Wiederaufnahmetermin;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verträge mit Unterbrechungen (#166, ADR 0023): Phasen, Abbuchungen, Status, Kündigung und Wiederaufnahme. Stichtag
 * ist der 01.11.2026 wie in {@link VertragsberechnungTests}.
 */
class UnterbrechungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Vertragsberechnung berechnung = new Vertragsberechnung(HEUTE);

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	private static Unterbrechung unterbrechung(String von, String bis) {
		return new Unterbrechung(d(von), d(bis), Unterbrechungsart.KUENDIGUNG);
	}

	/** Kündbar, ohne Laufzeitende, jederzeit zum Ende des Zahlungsintervalls, nicht gekündigt. */
	private static Vertragsdaten vertrag(Zahlweise zahlweise, String beginn, Unterbrechung... unterbrechungen) {
		return new Vertragsdaten(zahlweise, true, d(beginn), null, Verlaengerung.ZAHLUNGSINTERVALL, 0, null,
				List.of(unterbrechungen));
	}

	@Test
	void monatlichKeineAbbuchungenInDerUnterbrechungDanachNeuerTakt() {
		// Bis Mai am 15., Juni bis August unterbrochen, ab 01.09. wieder am 1.
		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", unterbrechung("2026-06-01", "2026-08-31"));

		assertThat(berechnung.kosten().abbuchungenImJahr(vertrag)).containsExactly(d("2026-01-15"), d("2026-02-15"),
				d("2026-03-15"), d("2026-04-15"), d("2026-05-15"), d("2026-09-01"), d("2026-10-01"), d("2026-11-01"),
				d("2026-12-01"));
	}

	@Test
	void vierteljaehrlichTaktAbDerWiederaufnahme() {
		// Ohne Unterbrechung am 10.02., 10.05., 10.08., 10.11.; die am 10.08. fällt in die Unterbrechung.
		var vertrag = vertrag(Zahlweise.VIERTELJAEHRLICH, "2025-02-10", unterbrechung("2026-06-01", "2026-08-31"));

		assertThat(berechnung.kosten().abbuchungenImJahr(vertrag)).containsExactly(d("2026-02-10"), d("2026-05-10"),
				d("2026-09-01"), d("2026-12-01"));
	}

	@Test
	void kostenImJahrNurFuerDieAbbuchungen() {
		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", unterbrechung("2026-06-01", "2026-08-31"));
		var preise = new Preisliste(List.of(new Preis(d("2025-11-15"), euro("10.00"))));

		assertThat(berechnung.kosten().kostenImJahr(vertrag, preise, Preisprognose.KEINE)).isEqualTo(euro("90.00"));
	}

	@Test
	void heuteInDerUnterbrechungUnterbrochenMitWiederAb() {
		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", unterbrechung("2026-10-01", "2026-12-31"));

		assertThat(berechnung.laufzeit().status(vertrag)).isEqualTo(Vertragsstatus.UNTERBROCHEN);
		var kuendigung = berechnung.laufzeit().kuendigung(vertrag);
		assertThat(kuendigung.art()).isEqualTo(Kuendigungsart.WIEDER_AB);
		assertThat(kuendigung.zum()).isEqualTo(d("2027-01-01"));
		assertThat(kuendigung.kuendigenBis()).isNull();
		assertThat(berechnung.laufzeit().wiederaufnahme(vertrag)).isEmpty();
	}

	@Test
	void unterbrochenOhneNaechstePreisaenderungUndPrognose() {
		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", unterbrechung("2026-10-01", "2026-12-31"));
		var preise = new Preisliste(List.of(new Preis(d("2025-11-15"), euro("10.00")),
				new Preis(d("2027-03-01"), euro("11.00"))));
		var prognose = new Preisprognose(new Preissteigerung(Steigerungsart.PROZENT, new BigDecimal("5"), null,
				Erhoehungsrhythmus.JAEHRLICH, d("2027-06-01")), null);

		assertThat(berechnung.preise().naechstePreisaenderung(vertrag, preise, prognose)).isEmpty();
		assertThat(berechnung.preise().prognose(vertrag, preise, prognose)).isEmpty();
		// Preise gelten nach Kalender weiter.
		assertThat(berechnung.preise().aktuellerPreis(vertrag, preise, prognose)).isEqualTo(euro("10.00"));
	}

	@Test
	void nachDerUnterbrechungWiederAktiv() {
		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", unterbrechung("2026-06-01", "2026-08-31"));

		assertThat(berechnung.laufzeit().status(vertrag)).isEqualTo(Vertragsstatus.AKTIV);
		assertThat(vertrag.aktuellerBeginn()).isEqualTo(d("2026-09-01"));
	}

	@Test
	void kuendigungZaehltAbDerWiederaufnahme() {
		// Ohne Unterbrechung lägen die Enden am 14.03. (Beginn 15.03.2020); ab der Wiederaufnahme am 01.05.2025 enden
		// die zwölf Monate am 30.04.
		var vertrag = new Vertragsdaten(Zahlweise.MONATLICH, true, d("2020-03-15"), null, Verlaengerung.ZWOELF_MONATE,
				1, null, List.of(unterbrechung("2025-01-01", "2025-04-30")));

		var kuendigung = berechnung.laufzeit().kuendigung(vertrag);

		assertThat(kuendigung.art()).isEqualTo(Kuendigungsart.KUENDIGEN_BIS);
		assertThat(kuendigung.zum()).isEqualTo(d("2027-04-30"));
		assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-03-31"));
	}

	@Test
	void wiederaufnahmeBeiBeendetFruehestensNachDemEndeVorschlagHeute() {
		var beendet = new Vertragsdaten(Zahlweise.MONATLICH, true, d("2025-11-15"), null,
				Verlaengerung.ZAHLUNGSINTERVALL, 0, d("2026-05-31"));

		assertThat(berechnung.laufzeit().wiederaufnahme(beendet))
				.contains(new Wiederaufnahmetermin(d("2026-06-01"), HEUTE));
	}

	@Test
	void wiederaufnahmeBeiGekuendigtVorschlagNachDemEnde() {
		var gekuendigt = new Vertragsdaten(Zahlweise.MONATLICH, true, d("2025-11-15"), null,
				Verlaengerung.ZAHLUNGSINTERVALL, 0, d("2026-12-31"));

		assertThat(berechnung.laufzeit().wiederaufnahme(gekuendigt))
				.contains(new Wiederaufnahmetermin(d("2027-01-01"), d("2027-01-01")));
	}

	@Test
	void wiederaufnahmeAuchBeiNichtKuendbaren() {
		var rundfunk = new Vertragsdaten(Zahlweise.VIERTELJAEHRLICH, false, d("2018-04-01"), null, null, null,
				d("2024-03-31"));

		assertThat(berechnung.laufzeit().wiederaufnahme(rundfunk)).contains(new Wiederaufnahmetermin(d("2024-04-01"), HEUTE));
	}

	@Test
	void keineWiederaufnahmeBeiAktivUndStorniert() {
		var aktiv = vertrag(Zahlweise.MONATLICH, "2025-11-15");
		var storniert = new Vertragsdaten(Zahlweise.MONATLICH, true, d("2026-12-01"), null,
				Verlaengerung.ZAHLUNGSINTERVALL, 0, d("2026-11-30"));

		assertThat(berechnung.laufzeit().wiederaufnahme(aktiv)).isEmpty();
		assertThat(berechnung.laufzeit().wiederaufnahme(storniert)).isEmpty();
	}

	@Test
	void unterbrechungenSortiert() {
		var spaeter = unterbrechung("2026-06-01", "2026-06-30");
		var frueher = unterbrechung("2026-02-01", "2026-02-28");

		var vertrag = vertrag(Zahlweise.MONATLICH, "2025-11-15", spaeter, frueher);

		assertThat(vertrag.unterbrechungen()).containsExactly(frueher, spaeter);
		assertThat(vertrag.aktuellerBeginn()).isEqualTo(d("2026-07-01"));
	}

}
