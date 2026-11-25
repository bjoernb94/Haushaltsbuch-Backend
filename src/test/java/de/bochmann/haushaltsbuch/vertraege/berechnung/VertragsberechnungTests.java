package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisaenderungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preispunkt;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preispunktart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeile;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Prognosepreis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.d;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.gekuendigt;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.gekuendigtZum;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.mit;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.vertrag;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose.KEINE;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart.AKTUELL;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart.ANGEKUENDIGT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart.FRUEHER;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart.NACH_ENDE;
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class VertragsberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Vertragsberechnung berechnung = new Vertragsberechnung(HEUTE);

	/** Der aktuelle Preis und die Art der Einträge in der Preisliste (#104, #105); heute ist der 01.11.2026. */
	@Nested
	class AktuellerPreis {

		private static Preisliste preise(Preis... preise) {
			return new Preisliste(List.of(preise));
		}

		/**
		 * {@code betrag} und der Eintrag {@link Preiszeilenart#AKTUELL} kommen aus derselben Bestimmung, auch in
		 * {@link Vertragsberechnung#werte}; {@code arten} in der Reihenfolge der sortierten Liste.
		 */
		private void aktuellIst(Vertragsdaten daten, Preisliste preise, String gueltigAb, String betrag,
				Preiszeilenart... arten) {
			var werte = berechnung.werte(new Berechnungsgrundlage(daten, preise, KEINE, Anteil.GANZ, List.of()));

			assertThat(werte.betrag()).isEqualTo(euro(betrag));
			assertThat(werte.preise()).isEqualTo(berechnung.preise().preiszeilen(daten, preise));
			assertThat(werte.preise()).filteredOn(zeile -> zeile.art() == AKTUELL).singleElement()
					.isEqualTo(new Preiszeile(d(gueltigAb), euro(betrag), AKTUELL));
			assertThat(werte.preise()).extracting(Preiszeile::art).containsExactly(arten);
		}

		@Test
		void preiswechselHeute() {
			var preise = preise(new Preis(d("2025-11-15"), euro("10.00")), new Preis(d("2026-11-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-11-01", "12.00", FRUEHER, AKTUELL);
		}

		@Test
		void angekuendigterPreisIstNichtAktuell() {
			var preise = preise(new Preis(d("2027-01-01"), euro("15.00")), new Preis(d("2025-11-15"), euro("10.00")),
					new Preis(d("2026-03-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-03-01", "12.00", FRUEHER, AKTUELL, ANGEKUENDIGT);
			assertThat(berechnung.preise().preiszeilen(vertrag(), preise)).extracting(Preiszeile::gueltigAb)
					.containsExactly(d("2025-11-15"), d("2026-03-01"), d("2027-01-01"));
		}

		@Test
		void kuenftigerBeginnDerErste() {
			var daten = mit(Zahlweise.MONATLICH, "2027-01-01", null, null, null);
			var preise = preise(new Preis(d("2027-01-01"), euro("10.00")), new Preis(d("2027-07-01"), euro("11.00")));

			aktuellIst(daten, preise, "2027-01-01", "10.00", AKTUELL, ANGEKUENDIGT);
		}

		/** Ein Eintrag nach dem Ende wurde nie gezahlt; am letzten Tag gilt er schon. */
		@Test
		void beendetDerAmLetztenTagGueltige() {
			var preise = preise(new Preis(d("2025-11-15"), euro("10.00")), new Preis(d("2026-07-01"), euro("12.00")),
					new Preis(d("2026-09-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2026-06-30"), preise, "2025-11-15", "10.00", AKTUELL, NACH_ENDE, NACH_ENDE);
			aktuellIst(gekuendigtZum("2026-07-01"), preise, "2026-07-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
			aktuellIst(gekuendigtZum("2026-10-31"), preise, "2026-09-01", "15.00", FRUEHER, FRUEHER, AKTUELL);
			aktuellIst(mit(Zahlweise.MONATLICH, "2025-11-15", "2026-08-31", Verlaengerung.KEINE, null), preise,
					"2026-07-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
		}

		@Test
		void beendetKostenProMonatMitDemAktuellenPreis() {
			var preise = preise(new Preis(d("2025-11-15"), euro("10.00")), new Preis(d("2026-09-01"), euro("15.00")));

			assertThat(berechnung.werte(new Berechnungsgrundlage(gekuendigtZum("2026-06-30"), preise, KEINE, Anteil.GANZ,
					List.of())).kostenProMonat()).isEqualTo(euro("10.00"));
		}

		/**
		 * Ein gekündigter, noch laufender Vertrag hat den heute gültigen Preis; ein späterer ist angekündigt, wenn er
		 * spätestens am letzten Tag beginnt, sonst nach dem Ende.
		 */
		@Test
		void gekuendigtDerHeuteGueltige() {
			var preise = preise(new Preis(d("2025-11-15"), euro("10.00")), new Preis(d("2026-10-01"), euro("12.00")),
					new Preis(d("2027-03-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2027-03-01"), preise, "2026-10-01", "12.00", FRUEHER, AKTUELL, ANGEKUENDIGT);
			aktuellIst(gekuendigtZum("2027-02-28"), preise, "2026-10-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
		}

	}

	/**
	 * Vergangene Erhöhungen (#159): Die Stufen der Preissteigerung bis heute sind fällig; beim Speichern legt das
	 * Backend sie als automatische Preise an, bis dahin sind sie errechnet. Heute ist hier der 22.11.2026.
	 */
	@Nested
	class VergangeneErhoehungen {

		private final Vertragsberechnung am22November = new Vertragsberechnung(d("2026-11-22"));

		/** Monatlich ab 01.03.2021. */
		private static final Vertragsdaten AB_2021 = mit(Zahlweise.MONATLICH, "2021-03-01", null, null, null);

		/** Jährlich +2&nbsp;% ab 01.03.2022. */
		private static final Preisprognose ZWEI_PROZENT = new Preisprognose(new Preissteigerung(Steigerungsart.PROZENT,
				new BigDecimal("2"), null, Erhoehungsrhythmus.JAEHRLICH, d("2022-03-01")), null);

		private static Preisliste ab2021(Preis... weitere) {
			return new Preisliste(List.of(new Preis(d("2021-03-01"), euro("100.00")))).mit(List.of(weitere));
		}

		private static Preis automatisch(String gueltigAb, String betrag) {
			return new Preis(d(gueltigAb), euro(betrag), Preisherkunft.AUTOMATISCH);
		}

		private Vertragswerte werte(Vertragsdaten daten, Preisliste preise, Preisprognose prognose) {
			return am22November.werte(new Berechnungsgrundlage(daten, preise, prognose, Anteil.GANZ, List.of()));
		}

		/** Das Beispiel aus dem Issue: jeder Schritt gerundet, die Prognose rechnet darauf weiter. */
		@Test
		void vertragAb2021MitJaehrlichZweiProzent() {
			assertThat(am22November.preise().faelligeStufen(AB_2021, ab2021(), ZWEI_PROZENT)).containsExactly(
					automatisch("2022-03-01", "102.00"), automatisch("2023-03-01", "104.04"),
					automatisch("2024-03-01", "106.12"), automatisch("2025-03-01", "108.24"),
					automatisch("2026-03-01", "110.40"));

			var werte = werte(AB_2021, ab2021(), ZWEI_PROZENT);
			assertThat(werte.betrag()).isEqualTo(euro("110.40"));
			assertThat(werte.betragErrechnet()).isTrue();
			assertThat(werte.kostenProMonat()).isEqualTo(euro("110.40"));
			// Januar und Februar zu 108,24 €, März bis Dezember zu 110,40 €.
			assertThat(werte.kostenImJahr()).isEqualTo(euro("1320.48"));
			assertThat(werte.kostenImJahrMitPrognose()).isTrue();
			// Gespeichert ist nur der Preis von 2021; er ist der, auf dem der aktuelle aufbaut.
			assertThat(werte.preise()).containsExactly(new Preiszeile(d("2021-03-01"), euro("100.00"), AKTUELL));
			assertThat(werte.prognose()).first().isEqualTo(new Prognosepreis(d("2027-03-01"), euro("112.61")));
			assertThat(werte.naechstePreisaenderung())
					.isEqualTo(new Preisaenderung(d("2027-03-01"), euro("112.61"), Preisaenderungsart.PROGNOSE));
			assertThat(werte.preisverlauf()).extracting(Preispunkt::art).containsExactly(Preispunktart.GEZAHLT,
					Preispunktart.ERRECHNET, Preispunktart.ERRECHNET, Preispunktart.ERRECHNET, Preispunktart.ERRECHNET,
					Preispunktart.ERRECHNET, Preispunktart.PROGNOSE, Preispunktart.PROGNOSE, Preispunktart.PROGNOSE);
		}

		/** Nach dem Speichern sind die Stufen gespeichert: nichts mehr fällig, nichts errechnet. */
		@Test
		void gespeicherteStufenSindNichtErrechnet() {
			var gespeichert = ab2021(automatisch("2022-03-01", "102.00"), automatisch("2023-03-01", "104.04"),
					automatisch("2024-03-01", "106.12"), automatisch("2025-03-01", "108.24"),
					automatisch("2026-03-01", "110.40"));

			assertThat(am22November.preise().faelligeStufen(AB_2021, gespeichert, ZWEI_PROZENT)).isEmpty();
			var werte = werte(AB_2021, gespeichert, ZWEI_PROZENT);
			assertThat(werte.betrag()).isEqualTo(euro("110.40"));
			assertThat(werte.betragErrechnet()).isFalse();
			assertThat(werte.kostenImJahrMitPrognose()).isFalse();
			assertThat(werte.preise()).extracting(Preiszeile::automatisch)
					.containsExactly(false, true, true, true, true, true);
			assertThat(werte.preise()).last()
					.isEqualTo(new Preiszeile(d("2026-03-01"), euro("110.40"), AKTUELL, true));
			assertThat(werte.preisverlauf()).extracting(Preispunkt::art).doesNotContain(Preispunktart.ERRECHNET);
			assertThat(werte.prognose()).first().isEqualTo(new Prognosepreis(d("2027-03-01"), euro("112.61")));
		}

		/** Eine seit dem Speichern fällig gewordene Stufe ist errechnet und setzt auf der letzten gespeicherten auf. */
		@Test
		void seitDemSpeichernFaellig() {
			var bis2025 = ab2021(automatisch("2022-03-01", "102.00"), automatisch("2023-03-01", "104.04"),
					automatisch("2024-03-01", "106.12"), automatisch("2025-03-01", "108.24"));

			assertThat(am22November.preise().faelligeStufen(AB_2021, bis2025, ZWEI_PROZENT))
					.containsExactly(automatisch("2026-03-01", "110.40"));
			var werte = werte(AB_2021, bis2025, ZWEI_PROZENT);
			assertThat(werte.betrag()).isEqualTo(euro("110.40"));
			assertThat(werte.betragErrechnet()).isTrue();
			assertThat(werte.preisverlauf()).filteredOn(punkt -> punkt.art() == Preispunktart.ERRECHNET)
					.containsExactly(new Preispunkt(d("2026-03-01"), euro("110.40"), Preispunktart.ERRECHNET));
		}

		/** Termine bis einschließlich der Garantie erhöhen nicht, auch vergangene. */
		@Test
		void preisgarantieUeberVergangeneTermine() {
			var mitGarantie = new Preisprognose(ZWEI_PROZENT.steigerung(), d("2024-03-01"));

			assertThat(am22November.preise().faelligeStufen(AB_2021, ab2021(), mitGarantie)).containsExactly(
					automatisch("2025-03-01", "102.00"), automatisch("2026-03-01", "104.04"));
		}

		/** Zwischen zwei Einträgen rechnet die Regel nicht, die Lücke ist gewollt; nach dem letzten schon. */
		@Test
		void eingetragenerPreisZwischenDenTerminen() {
			var preise = ab2021(new Preis(d("2024-07-01"), euro("120.00")));

			assertThat(am22November.preise().faelligeStufen(AB_2021, preise, ZWEI_PROZENT)).containsExactly(
					automatisch("2025-03-01", "122.40"), automatisch("2026-03-01", "124.85"));
		}

		/** Ein angekündigter Preis verhindert die Stufen bis heute nicht; die Prognose setzt bei ihm an. */
		@Test
		void angekuendigterPreisInDerZukunft() {
			var preise = ab2021(new Preis(d("2027-06-01"), euro("130.00")));

			assertThat(am22November.preise().faelligeStufen(AB_2021, preise, ZWEI_PROZENT)).hasSize(5).last()
					.isEqualTo(automatisch("2026-03-01", "110.40"));
			var werte = werte(AB_2021, preise, ZWEI_PROZENT);
			assertThat(werte.betrag()).isEqualTo(euro("110.40"));
			assertThat(werte.preise()).extracting(Preiszeile::art).containsExactly(AKTUELL, ANGEKUENDIGT);
			assertThat(werte.naechstePreisaenderung())
					.isEqualTo(new Preisaenderung(d("2027-06-01"), euro("130.00"), Preisaenderungsart.ANGEKUENDIGT));
			assertThat(werte.prognose()).first().isEqualTo(new Prognosepreis(d("2028-03-01"), euro("132.60")));
		}

		/** Bei einem beendeten Vertrag gilt der Preis am letzten Tag; Stufen danach zählen nicht. */
		@Test
		void beendeterVertrag() {
			var beendet = gekuendigt("2021-03-01", "2024-12-31");

			assertThat(am22November.preise().faelligeStufen(beendet, ab2021(), ZWEI_PROZENT)).extracting(Preis::gueltigAb)
					.containsExactly(d("2022-03-01"), d("2023-03-01"), d("2024-03-01"));
			var werte = werte(beendet, ab2021(), ZWEI_PROZENT);
			assertThat(werte.betrag()).isEqualTo(euro("106.12"));
			assertThat(werte.prognose()).isEmpty();
			assertThat(werte.naechstePreisaenderung()).isNull();
		}

		/** Vierteljährlich über mehrere Jahre, mit festem Betrag. */
		@Test
		void vierteljaehrlichUeberMehrereJahre() {
			var daten = mit(Zahlweise.MONATLICH, "2024-01-15", null, null, null);
			var preise = new Preisliste(List.of(new Preis(d("2024-01-15"), euro("10.00"))));
			var prognose = new Preisprognose(new Preissteigerung(Steigerungsart.BETRAG, null, euro("1.00"),
					Erhoehungsrhythmus.VIERTELJAEHRLICH, d("2024-04-15")), null);

			// 2024: April, Juli, Oktober; 2025: vier; 2026: vier.
			assertThat(am22November.preise().faelligeStufen(daten, preise, prognose)).hasSize(11).last()
					.isEqualTo(automatisch("2026-10-15", "21.00"));
			assertThat(am22November.preise().aktuellerPreis(daten, preise, prognose)).isEqualTo(euro("21.00"));
			assertThat(am22November.preise().prognose(daten, preise, prognose)).first()
					.isEqualTo(new Prognosepreis(d("2027-01-15"), euro("22.00")));
		}

		/** Ein Termin am Monatsletzten bleibt am Monatsletzten (30.04. → 31.07.), auch in der Prognose. */
		@Test
		void monatsendeBleibtMonatsende() {
			var daten = mit(Zahlweise.MONATLICH, "2026-01-31", null, null, null);
			var preise = new Preisliste(List.of(new Preis(d("2026-01-31"), euro("10.00"))));
			var prognose = new Preisprognose(new Preissteigerung(Steigerungsart.BETRAG, null, euro("1.00"),
					Erhoehungsrhythmus.VIERTELJAEHRLICH, Erhoehungsrhythmus.VIERTELJAEHRLICH.nach(d("2026-01-31"))), null);

			assertThat(am22November.preise().faelligeStufen(daten, preise, prognose)).extracting(Preis::gueltigAb)
					.containsExactly(d("2026-04-30"), d("2026-07-31"), d("2026-10-31"));
			assertThat(am22November.preise().prognose(daten, preise, prognose)).extracting(Prognosepreis::ab)
					.containsExactly(d("2027-01-31"), d("2027-04-30"), d("2027-07-31"));
		}

		/** Vor dem Beginn ist nichts fällig; storniert auch nicht. */
		@Test
		void nichtBegonnenUndStorniertOhneStufen() {
			var spaeter = mit(Zahlweise.MONATLICH, "2027-01-01", null, null, null);
			var preise = new Preisliste(List.of(new Preis(d("2027-01-01"), euro("10.00"))));

			assertThat(am22November.preise().faelligeStufen(spaeter, preise, ZWEI_PROZENT)).isEmpty();
			assertThat(am22November.preise().faelligeStufen(gekuendigt("2027-01-01", "2026-12-31"), preise, ZWEI_PROZENT))
					.isEmpty();
		}

		/** Wie nach dem Speichern: Die Vorschau rechnet mit den Stufen als gespeicherten. */
		@Test
		void wieGespeichert() {
			var grundlage = am22November.wieGespeichert(
					new Berechnungsgrundlage(AB_2021, ab2021(), ZWEI_PROZENT, Anteil.GANZ, List.of()));

			assertThat(grundlage.preise().preise()).hasSize(6);
			var werte = am22November.werte(grundlage);
			assertThat(werte.betrag()).isEqualTo(euro("110.40"));
			assertThat(werte.betragErrechnet()).isFalse();
			assertThat(werte.preise()).filteredOn(Preiszeile::automatisch).hasSize(5);
		}

		/** Bis zum 22.11.2026 betrachtet, also schon gespeichert: Gelöschte Stufen entstehen nicht neu (#162). */
		private static final Preisprognose BETRACHTET = ZWEI_PROZENT.betrachtetBis(d("2026-11-22"));

		/** Fall A: Die neueste Stufe gelöscht; die Prognose rechnet vom letzten verbliebenen Eintrag weiter. */
		@Test
		void geloeschteNeuesteStufeBleibtWeg() {
			var ohne2026 = ab2021(automatisch("2022-03-01", "102.00"), automatisch("2023-03-01", "104.04"),
					automatisch("2024-03-01", "106.12"), automatisch("2025-03-01", "108.24"));

			assertThat(am22November.preise().faelligeStufen(AB_2021, ohne2026, BETRACHTET)).isEmpty();
			var werte = werte(AB_2021, ohne2026, BETRACHTET);
			assertThat(werte.betrag()).isEqualTo(euro("108.24"));
			assertThat(werte.betragErrechnet()).isFalse();
			assertThat(werte.preisverlauf()).extracting(Preispunkt::art).doesNotContain(Preispunktart.ERRECHNET);
			assertThat(werte.prognose()).first().isEqualTo(new Prognosepreis(d("2027-03-01"), euro("110.40")));
		}

		/** Fall C: alle automatischen gelöscht; nur der eingetragene Preis bleibt. */
		@Test
		void alleGeloeschtenStufenBleibenWeg() {
			assertThat(am22November.preise().faelligeStufen(AB_2021, ab2021(), BETRACHTET)).isEmpty();
			var werte = werte(AB_2021, ab2021(), BETRACHTET);
			assertThat(werte.betrag()).isEqualTo(euro("100.00"));
			assertThat(werte.betragErrechnet()).isFalse();
		}

		/** Ein Jahr später ist nur der neue Termin fällig, die gelöschten davor bleiben weg. */
		@Test
		void nachDemBetrachtetenTagWiederFaellig() {
			var einJahrSpaeter = new Vertragsberechnung(d("2027-11-22"));
			var ohne2026 = ab2021(automatisch("2022-03-01", "102.00"), automatisch("2023-03-01", "104.04"),
					automatisch("2024-03-01", "106.12"), automatisch("2025-03-01", "108.24"));

			assertThat(einJahrSpaeter.preise().faelligeStufen(AB_2021, ohne2026, BETRACHTET))
					.containsExactly(automatisch("2027-03-01", "110.40"));
			assertThat(einJahrSpaeter.preise().aktuellerPreis(AB_2021, ohne2026, BETRACHTET)).isEqualTo(euro("110.40"));
		}

		/** Nur nach hinten: Ein früherer Tag ändert nichts. */
		@Test
		void betrachtetBisWirdNurSpaeter() {
			assertThat(BETRACHTET.betrachtetBis(d("2024-12-31")).betrachtetBis()).isEqualTo(d("2026-11-22"));
			assertThat(ZWEI_PROZENT.betrachtetBis(d("2024-12-31")).betrachtetBis()).isEqualTo(d("2024-12-31"));
		}

		/** Die Vorschau rechnet wie nach dem Speichern, also bis heute betrachtet. */
		@Test
		void wieGespeichertIstBetrachtet() {
			var grundlage = am22November.wieGespeichert(
					new Berechnungsgrundlage(AB_2021, ab2021(), ZWEI_PROZENT, Anteil.GANZ, List.of()));

			assertThat(grundlage.prognose().betrachtetBis()).isEqualTo(d("2026-11-22"));
		}

		@Test
		void vorschlagEinRhythmusNachDemBeginn() {
			assertThat(Erhoehungsrhythmus.JAEHRLICH.nach(d("2021-03-01"))).isEqualTo(d("2022-03-01"));
			assertThat(Erhoehungsrhythmus.VIERTELJAEHRLICH.nach(d("2026-01-31"))).isEqualTo(d("2026-04-30"));
			assertThat(Erhoehungsrhythmus.VIERTELJAEHRLICH.nach(d("2026-01-30"))).isEqualTo(d("2026-04-30"));
			assertThat(Erhoehungsrhythmus.JAEHRLICH.nach(d("2024-02-29"))).isEqualTo(d("2025-02-28"));
		}

	}

}
