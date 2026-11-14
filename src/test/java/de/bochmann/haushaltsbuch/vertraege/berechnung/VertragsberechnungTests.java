package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.BEENDET;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.ENDET;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.GEKUENDIGT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.KEINE_LAUFZEIT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.KUENDIGEN_BIS;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.MONATLICH_KUENDBAR;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.NICHT_KUENDBAR;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preisprognose.KEINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class VertragsberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Vertragsberechnung berechnung = new Vertragsberechnung(HEUTE);

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	/** Monatlich, kündbar, sonst leer; die Tests setzen nur, was sie brauchen. */
	private static Vertragsdaten vertrag() {
		return new Vertragsdaten(Zahlweise.MONATLICH, d("2026-11-15"), true, null, null, null, null, null);
	}

	private static Vertragsdaten mit(Zahlweise zahlweise, String abbuchungAb, String beginn, String laufzeitende,
			Verlaengerung verlaengerung, Integer frist) {
		return new Vertragsdaten(zahlweise, abbuchungAb == null ? null : d(abbuchungAb), true,
				beginn == null ? null : d(beginn), laufzeitende == null ? null : d(laufzeitende), verlaengerung, frist,
				null);
	}

	private static Vertragsdaten gekuendigtZum(String datum) {
		return new Vertragsdaten(Zahlweise.MONATLICH, d("2026-11-15"), true, null, null, null, 1, d(datum));
	}

	@Nested
	class Status {

		@Test
		void ohneKuendigungAktiv() {
			assertThat(berechnung.status(vertrag())).isEqualTo(Vertragsstatus.AKTIV);
		}

		@Test
		void gekuendigtBisZumKuendigungsdatum() {
			assertThat(berechnung.status(gekuendigtZum("2026-12-31"))).isEqualTo(Vertragsstatus.GEKUENDIGT);
			assertThat(berechnung.status(gekuendigtZum("2026-11-01"))).isEqualTo(Vertragsstatus.GEKUENDIGT);
		}

		@Test
		void beendetNachDemKuendigungsdatum() {
			assertThat(berechnung.status(gekuendigtZum("2026-10-31"))).isEqualTo(Vertragsstatus.BEENDET);
		}

		@Test
		void ohneVerlaengerungBeendetNachDemLaufzeitende() {
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, null, "2026-10-31", Verlaengerung.KEINE, null)))
					.isEqualTo(Vertragsstatus.BEENDET);
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, null, "2026-11-01", Verlaengerung.KEINE, null)))
					.isEqualTo(Vertragsstatus.AKTIV);
		}

		@Test
		void mitVerlaengerungLaeuftNachDemLaufzeitendeWeiter() {
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, null, "2025-10-31", Verlaengerung.MONATLICH, 1)))
					.isEqualTo(Vertragsstatus.AKTIV);
		}

	}

	/** Der aktuelle Preis und sein Eintrag in der Preisliste (#104); heute ist der 01.11.2026. */
	@Nested
	class AktuellerPreis {

		private static Preisliste preise(Preis... preise) {
			return new Preisliste(List.of(preise));
		}

		/** {@code betrag} und {@code aktuell} kommen aus derselben Bestimmung, auch in {@link Vertragsberechnung#werte}. */
		private void aktuellIst(Vertragsdaten daten, Preisliste preise, String gueltigAb, String betrag) {
			var werte = berechnung.werte(new Berechnungsgrundlage(daten, preise, KEINE, Anteil.GANZ, List.of()));

			assertThat(werte.betrag()).isEqualTo(euro(betrag));
			assertThat(werte.preise()).isEqualTo(berechnung.preiszeilen(daten, preise));
			assertThat(werte.preise()).filteredOn(Preiszeile::aktuell).singleElement()
					.isEqualTo(new Preiszeile(gueltigAb == null ? null : d(gueltigAb), euro(betrag), true));
		}

		@Test
		void preiswechselHeute() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-11-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-11-01", "12.00");
			assertThat(berechnung.preiszeilen(vertrag(), preise)).extracting(Preiszeile::aktuell).containsExactly(false, true);
		}

		@Test
		void angekuendigterPreisIstNichtAktuell() {
			var preise = preise(new Preis(d("2027-01-01"), euro("15.00")), new Preis(null, euro("10.00")),
					new Preis(d("2026-03-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-03-01", "12.00");
			assertThat(berechnung.preiszeilen(vertrag(), preise)).extracting(Preiszeile::gueltigAb, Preiszeile::aktuell)
					.containsExactly(tuple(null, false), tuple(d("2026-03-01"), true), tuple(d("2027-01-01"), false));
		}

		@Test
		void kuenftigerBeginnDerErste() {
			var daten = mit(Zahlweise.MONATLICH, "2027-01-01", "2027-01-01", null, null, null);
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2027-07-01"), euro("11.00")));

			aktuellIst(daten, preise, null, "10.00");
		}

		/** Ein Eintrag nach dem Ende wurde nie gezahlt; am letzten Tag gilt er schon. */
		@Test
		void beendetDerAmLetztenTagGueltige() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-07-01"), euro("12.00")),
					new Preis(d("2026-09-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2026-06-30"), preise, null, "10.00");
			aktuellIst(gekuendigtZum("2026-07-01"), preise, "2026-07-01", "12.00");
			aktuellIst(gekuendigtZum("2026-10-31"), preise, "2026-09-01", "15.00");
			aktuellIst(mit(Zahlweise.MONATLICH, null, null, "2026-08-31", Verlaengerung.KEINE, null), preise,
					"2026-07-01", "12.00");
		}

		@Test
		void beendetKostenProMonatMitDemAktuellenPreis() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-09-01"), euro("15.00")));

			assertThat(berechnung.werte(new Berechnungsgrundlage(gekuendigtZum("2026-06-30"), preise, KEINE, Anteil.GANZ,
					List.of())).kostenProMonat()).isEqualTo(euro("10.00"));
		}

		/** Ein gekündigter, noch laufender Vertrag hat den heute gültigen Preis. */
		@Test
		void gekuendigtDerHeuteGueltige() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-10-01"), euro("12.00")),
					new Preis(d("2027-03-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2026-12-31"), preise, "2026-10-01", "12.00");
		}

	}

	@Nested
	class NaechsteAbbuchung {

		@Test
		void kuenftigesDatumBleibt() {
			assertThat(berechnung.naechsteAbbuchung(vertrag())).contains(d("2026-11-15"));
		}

		@Test
		void heuteZaehltNochAlsNaechste() {
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-11-01", null, null, null, null)))
					.contains(HEUTE);
		}

		@Test
		void zaehltImTaktDerZahlweiseWeiter() {
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.VIERTELJAEHRLICH, "2026-10-01", null, null, null, null)))
					.contains(d("2027-01-01"));
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.JAEHRLICH, "2020-07-01", null, null, null, null)))
					.contains(d("2027-07-01"));
		}

		@Test
		void zaehltVomGespeichertenDatumOhneAbrutschen() {
			// 31.01. + 10 Monate = 30.11.; Schritt für Schritt wäre über den Februar der 28. daraus geworden.
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-01-31", null, null, null, null)))
					.contains(d("2026-11-30"));
		}

		@Test
		void keineNachDemKuendigungsdatum() {
			var gekuendigt = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-20"), true, null, null, null, 1,
					d("2026-11-15"));
			assertThat(berechnung.naechsteAbbuchung(gekuendigt)).isEmpty();

			var nochEineAbbuchung = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-10"), true, null, null, null, 1,
					d("2026-11-15"));
			assertThat(berechnung.naechsteAbbuchung(nochEineAbbuchung)).contains(d("2026-11-10"));
		}

		@Test
		void keineNachDemLaufzeitendeOhneVerlaengerung() {
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-10-20", null, "2026-11-30",
					Verlaengerung.KEINE, null))).contains(d("2026-11-20"));
			// Eine Abbuchung genau am letzten Tag bleibt.
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-10-20", null, "2026-11-20",
					Verlaengerung.KEINE, null))).contains(d("2026-11-20"));
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-10-20", null, "2026-11-15",
					Verlaengerung.KEINE, null))).isEmpty();
		}

		@Test
		void laufzeitendeZaehltNurBeiVertraegenDieDamitEnden() {
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-10-20", null, "2026-11-15", null, null)))
					.contains(d("2026-11-20"));
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, "2026-10-20", null, "2026-11-15",
					Verlaengerung.MONATLICH, null))).contains(d("2026-11-20"));
			var nichtKuendbar = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-20"), false, null, d("2026-11-15"),
					Verlaengerung.KEINE, null, null);
			assertThat(berechnung.naechsteAbbuchung(nichtKuendbar)).contains(d("2026-11-20"));
		}

		@Test
		void beiKuendigungsdatumUndLaufzeitendeZaehltDasFruehere() {
			var kuendigungZuerst = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-20"), true, null, d("2026-12-15"),
					Verlaengerung.KEINE, null, d("2026-11-15"));
			assertThat(berechnung.naechsteAbbuchung(kuendigungZuerst)).isEmpty();

			// Unstimmige Eingabe: kuendigung nennt weiter gekuendigtZum, abgebucht wird trotzdem nicht nach dem Laufzeitende.
			var laufzeitendeZuerst = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-20"), true, null, d("2026-11-15"),
					Verlaengerung.KEINE, null, d("2026-12-15"));
			assertThat(berechnung.naechsteAbbuchung(laufzeitendeZuerst)).isEmpty();
			assertThat(berechnung.kuendigung(laufzeitendeZuerst).art()).isEqualTo(Kuendigungsart.GEKUENDIGT);
			assertThat(berechnung.kuendigung(laufzeitendeZuerst).zum()).isEqualTo(d("2026-12-15"));

			var beideDanach = new Vertragsdaten(Zahlweise.MONATLICH, d("2026-10-20"), true, null, d("2026-11-30"),
					Verlaengerung.KEINE, null, d("2026-11-25"));
			assertThat(berechnung.naechsteAbbuchung(beideDanach)).contains(d("2026-11-20"));
		}

		@Test
		void keineBeiBeendetenVertraegenOderFehlendenAngaben() {
			assertThat(berechnung.naechsteAbbuchung(gekuendigtZum("2026-10-31"))).isEmpty();
			assertThat(berechnung.naechsteAbbuchung(mit(null, "2026-11-15", null, null, null, null))).isEmpty();
			assertThat(berechnung.naechsteAbbuchung(mit(Zahlweise.MONATLICH, null, null, null, null, null))).isEmpty();
		}

	}

	@Nested
	class AbbuchungenImJahr {

		/** Der Anker liegt meist in der Zukunft („Nächste Abbuchung“); gezählt wird auch rückwärts bis Januar. */
		@Test
		void ankerInDerZukunftZaehltRueckwaertsBisJanuar() {
			assertThat(berechnung.abbuchungenImJahr(vertrag())).hasSize(12)
					.startsWith(d("2026-01-15"))
					.endsWith(d("2026-12-15"));
		}

		@Test
		void nichtVorDemBeginn() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2026-11-15", "2026-04-20", null, null, null)))
					.containsExactly(d("2026-05-15"), d("2026-06-15"), d("2026-07-15"), d("2026-08-15"), d("2026-09-15"),
							d("2026-10-15"), d("2026-11-15"), d("2026-12-15"));
		}

		@Test
		void nurBisZumEnde() {
			assertThat(berechnung.abbuchungenImJahr(gekuendigtZum("2026-03-15")))
					.containsExactly(d("2026-01-15"), d("2026-02-15"), d("2026-03-15"));
			var endet = mit(Zahlweise.VIERTELJAEHRLICH, "2026-02-01", null, "2026-06-30", Verlaengerung.KEINE, null);
			assertThat(berechnung.abbuchungenImJahr(endet)).containsExactly(d("2026-02-01"), d("2026-05-01"));
		}

		@Test
		void jaehrlichEineAbbuchung() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.JAEHRLICH, "2020-07-01", null, null, null, null)))
					.containsExactly(d("2026-07-01"));
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.JAEHRLICH, "2028-03-01", null, null, null, null)))
					.containsExactly(d("2026-03-01"));
		}

		/** Wie bei der nächsten Abbuchung vom Anker aus gezählt, auch rückwärts: 31.01. → 28.02. → 31.03. */
		@Test
		void monatsendeRutschtNichtAb() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2026-12-31", null, null, null, null)))
					.contains(d("2026-02-28"), d("2026-03-31"), d("2026-04-30"));
		}

		@Test
		void beendetInEinemFruherenJahrKeine() {
			assertThat(berechnung.abbuchungenImJahr(gekuendigtZum("2025-12-31"))).isEmpty();
		}

		@Test
		void ohneAngabenKeine() {
			assertThat(berechnung.abbuchungenImJahr(mit(null, "2026-11-15", null, null, null, null))).isEmpty();
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, null, null, null, null, null))).isEmpty();
		}

		@Test
		void kostenImJahrMitPreiswechsel() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2026-07-01"), euro("20.00"))));

			// Januar bis Juni zu 10 €, Juli bis Dezember zu 20 €.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.MONATLICH, "2026-11-01", null, null, null, null), preise, KEINE))
					.isEqualTo(euro("180.00"));
			// Jährlich im Oktober: nur der neue Preis.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.JAEHRLICH, "2026-10-01", null, null, null, null), preise, KEINE))
					.isEqualTo(euro("20.00"));
		}

		@Test
		void naechsteAbbuchungMitPreisDesTages() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2026-11-15"), euro("12.00"))));

			assertThat(berechnung.naechsteAbbuchung(vertrag(), preise, KEINE)).contains(new Abbuchung(d("2026-11-15"), euro("12.00")));
		}

		@Test
		void keinePreisaenderungBeiBeendetemVertrag() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-01-01"), euro("12.00"))));

			assertThat(berechnung.naechstePreisaenderung(vertrag(), preise, KEINE)).isPresent();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-12-31"), preise, KEINE)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-10-31"), preise, KEINE)).isEmpty();
		}

	}

	/** Prognose aus der Preissteigerung (#96); heute ist der 01.11.2026. */
	@Nested
	class Prognose {

		private static Preisliste preis(String wert) {
			return new Preisliste(List.of(new Preis(null, euro(wert))));
		}

		private static Preisprognose prozent(String prozent, Erhoehungsrhythmus rhythmus, String naechsteAm,
				String garantieBis) {
			return new Preisprognose(new Preissteigerung(Steigerungsart.PROZENT, new BigDecimal(prozent), null, rhythmus,
					d(naechsteAm)), garantieBis == null ? null : d(garantieBis));
		}

		private static Preisprognose betrag(String betrag, Erhoehungsrhythmus rhythmus, String naechsteAm) {
			return new Preisprognose(new Preissteigerung(Steigerungsart.BETRAG, null, euro(betrag), rhythmus,
					d(naechsteAm)), null);
		}

		/** Jeder Schritt gerundet, der nächste rechnet mit dem gerundeten Preis weiter (ADR 0011). */
		@Test
		void prozentRundetJedenSchritt() {
			var prognose = prozent("2.5", Erhoehungsrhythmus.JAEHRLICH, "2027-01-01", null);

			assertThat(berechnung.prognose(vertrag(), preis("100.00"), prognose)).containsExactly(
					new Prognosepreis(d("2027-01-01"), euro("102.50")),
					new Prognosepreis(d("2028-01-01"), euro("105.06")),
					new Prognosepreis(d("2029-01-01"), euro("107.69")));
		}

		@Test
		void festerBetragImRhythmus() {
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-12-01");

			assertThat(berechnung.prognose(vertrag(), preis("9.99"), prognose)).containsExactly(
					new Prognosepreis(d("2026-12-01"), euro("10.99")),
					new Prognosepreis(d("2027-03-01"), euro("11.99")),
					new Prognosepreis(d("2027-06-01"), euro("12.99")));
		}

		/** Erst nach dem letzten Eintrag, ein Termin genau an seinem Tag zählt nicht; angekündigt kommt zuerst. */
		@Test
		void setztBeimLetztenPreisAn() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-02-01"), euro("12.00"))));
			var prognose = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2026-02-01", null);

			assertThat(berechnung.prognose(vertrag(), preise, prognose)).first()
					.isEqualTo(new Prognosepreis(d("2028-02-01"), euro("13.20")));
			assertThat(berechnung.naechstePreisaenderung(vertrag(), preise, prognose))
					.contains(new Preisaenderung(d("2027-02-01"), euro("12.00"), Preisaenderungsart.ANGEKUENDIGT));
		}

		@Test
		void ohneAngekuendigtenPreisIstDiePrognoseDieNaechsteAenderung() {
			var prognose = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2027-03-01", null);

			assertThat(berechnung.naechstePreisaenderung(vertrag(), preis("100.00"), prognose))
					.contains(new Preisaenderung(d("2027-03-01"), euro("110.00"), Preisaenderungsart.PROGNOSE));
		}

		/** Bis einschließlich des Garantietags keine Erhöhung. */
		@Test
		void nichtInDerPreisgarantie() {
			var prognose = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2026-12-01", "2027-12-01");

			assertThat(berechnung.prognose(vertrag(), preis("100.00"), prognose)).first()
					.isEqualTo(new Prognosepreis(d("2028-12-01"), euro("110.00")));
		}

		/** Vergangene Termine erhöhen nichts; ein Termin heute zählt. */
		@Test
		void abHeute() {
			assertThat(berechnung.prognose(vertrag(), preis("100.00"),
					prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2020-03-01", null)))
					.first().isEqualTo(new Prognosepreis(d("2027-03-01"), euro("110.00")));
			assertThat(berechnung.prognose(vertrag(), preis("100.00"),
					prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2020-11-01", null)))
					.first().isEqualTo(new Prognosepreis(d("2026-11-01"), euro("110.00")));
		}

		@Test
		void nichtNachDemVertragsende() {
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-12-01");

			assertThat(berechnung.prognose(gekuendigtZum("2027-04-30"), preis("10.00"), prognose))
					.extracting(Prognosepreis::ab).containsExactly(d("2026-12-01"), d("2027-03-01"));
			assertThat(berechnung.prognose(gekuendigtZum("2026-10-31"), preis("10.00"), prognose)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-10-31"), preis("10.00"), prognose)).isEmpty();
		}

		@Test
		void ohneRegelKeine() {
			assertThat(berechnung.prognose(vertrag(), preis("10.00"), KEINE)).isEmpty();
			assertThat(berechnung.prognose(vertrag(), preis("10.00"), new Preisprognose(null, d("2027-01-01")))).isEmpty();
		}

		/** Monatlich am 15.: Januar bis November zu 10 €, Dezember zur Prognose 11 €. */
		@Test
		void kostenImJahrZaehltDiePrognose() {
			var daten = mit(Zahlweise.MONATLICH, "2026-11-15", null, null, null, null);
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-12-01");

			assertThat(berechnung.kostenImJahr(daten, preis("10.00"), prognose)).isEqualTo(euro("121.00"));
			assertThat(berechnung.kostenImJahrMitPrognose(daten, preis("10.00"), prognose)).isTrue();
			// Erhöhung erst im nächsten Jahr bzw. ohne Regel.
			assertThat(berechnung.kostenImJahrMitPrognose(daten, preis("10.00"),
					betrag("1.00", Erhoehungsrhythmus.JAEHRLICH, "2027-01-01"))).isFalse();
			assertThat(berechnung.kostenImJahrMitPrognose(daten, preis("10.00"), KEINE)).isFalse();
		}

		/** Am 01.01. vierteljährlich: vier Erhöhungen im Jahr, mehr als die drei angezeigten. */
		@Test
		void kostenImJahrMitAllenTerminenImJahr() {
			var januar = new Vertragsberechnung(d("2026-01-01"));
			var daten = mit(Zahlweise.MONATLICH, "2026-01-15", null, null, null, null);
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-01-01");

			// Je drei Abbuchungen zu 11, 12, 13 und 14 €.
			assertThat(januar.kostenImJahr(daten, preis("10.00"), prognose)).isEqualTo(euro("150.00"));
			assertThat(januar.prognose(daten, preis("10.00"), prognose)).hasSize(3);
		}

		@Test
		void naechsteAbbuchungMitPrognosepreis() {
			var prognose = betrag("2.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-11-10");

			assertThat(berechnung.naechsteAbbuchung(vertrag(), preis("10.00"), prognose))
					.contains(new Abbuchung(d("2026-11-15"), euro("12.00")));
		}

		@Test
		void preisverlaufMitAllenArten() {
			var daten = mit(Zahlweise.MONATLICH, "2026-11-15", "2024-01-01", null, null, null);
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2025-06-01"), euro("11.00")),
					new Preis(d("2027-01-01"), euro("12.00"))));
			var prognose = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2027-06-01", null);

			assertThat(berechnung.preisverlauf(daten, preise, prognose)).containsExactly(
					new Preispunkt(d("2024-01-01"), euro("10.00"), Preispunktart.GEZAHLT),
					new Preispunkt(d("2025-06-01"), euro("11.00"), Preispunktart.GEZAHLT),
					new Preispunkt(d("2027-01-01"), euro("12.00"), Preispunktart.ANGEKUENDIGT),
					new Preispunkt(d("2027-06-01"), euro("13.20"), Preispunktart.PROGNOSE),
					new Preispunkt(d("2028-06-01"), euro("14.52"), Preispunktart.PROGNOSE),
					new Preispunkt(d("2029-06-01"), euro("15.97"), Preispunktart.PROGNOSE));
		}

		/** Ohne Beginn 12 Monate vor dem zweiten Eintrag, sonst vor heute; nach dem Vertragsende nichts. */
		@Test
		void preisverlaufStartdatumUndVertragsende() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-06-01"), euro("11.00"))));

			assertThat(berechnung.preisverlauf(vertrag(), preise, KEINE)).extracting(Preispunkt::ab)
					.containsExactly(d("2026-06-01"), d("2027-06-01"));
			assertThat(berechnung.preisverlauf(vertrag(), preis("10.00"), KEINE)).extracting(Preispunkt::ab)
					.containsExactly(d("2025-11-01"));
			assertThat(berechnung.preisverlauf(gekuendigtZum("2027-05-31"), preise, KEINE)).extracting(Preispunkt::art)
					.containsExactly(Preispunktart.GEZAHLT);
		}

	}

	@Nested
	class Kuendigungen {

		@Test
		void kfzVersicherungMitZwoelfMonatenVerlaengerung() {
			var kfz = mit(Zahlweise.JAEHRLICH, "2027-01-01", "2023-01-01", "2023-12-31", Verlaengerung.ZWOELF_MONATE, 1);
			assertThat(berechnung.kuendigung(kfz)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2026-11-30"), d("2026-12-31"), 1, true));
		}

		@Test
		void verlaengertWeiterWennDieFristVerpasstIst() {
			// Ende 30.09.2026 mit 3 Monaten Frist wäre bis 30.06.2026 zu kündigen gewesen.
			var haftpflicht = mit(Zahlweise.VIERTELJAEHRLICH, "2026-10-01", "2020-10-01", "2021-09-30",
					Verlaengerung.ZWOELF_MONATE, 3);
			var kuendigung = berechnung.kuendigung(haftpflicht);
			assertThat(kuendigung.art()).isEqualTo(KUENDIGEN_BIS);
			assertThat(kuendigung.zum()).isEqualTo(d("2027-09-30"));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-06-30"));
			assertThat(kuendigung.dringend()).isFalse();
		}

		@Test
		void monatsendeBleibtMonatsende() {
			// 30.06. minus 3 Monate ist der 31.03., nicht der 30.03.
			var hausrat = mit(Zahlweise.JAEHRLICH, "2027-07-01", "2021-07-01", "2022-06-30", Verlaengerung.ZWOELF_MONATE, 3);
			var kuendigung = berechnung.kuendigung(hausrat);
			assertThat(kuendigung.zum()).isEqualTo(d("2027-06-30"));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-03-31"));
		}

		@Test
		void vorDemLaufzeitendeKuendigenBisAuchBeiMonatlicherVerlaengerung() {
			var mobilfunk = mit(Zahlweise.MONATLICH, "2026-11-15", "2025-03-15", "2027-03-14", Verlaengerung.MONATLICH, 1);
			assertThat(berechnung.kuendigung(mobilfunk)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-02-14"), d("2027-03-14"), 1, false));
		}

		@Test
		void nachDemLaufzeitendeMonatlichKuendbar() {
			var mobilfunk = mit(Zahlweise.MONATLICH, "2026-11-15", "2024-03-15", "2026-03-14", Verlaengerung.MONATLICH, 1);
			assertThat(berechnung.kuendigung(mobilfunk)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-14"), d("2026-12-14"), 1, false));
		}

		@Test
		void ohneLaufzeitendeZumEndeDesZahlungsintervalls() {
			// Ende = Tag vor der Abbuchung (31.10.), monatlich weitergezählt mit Monatsende, 3 Monate Frist.
			var fitness = mit(Zahlweise.MONATLICH, "2026-11-01", "2025-05-01", null, Verlaengerung.ZAHLUNGSINTERVALL, 3);
			assertThat(berechnung.kuendigung(fitness)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-30"), d("2027-02-28"), 3, false));

			var streaming = mit(Zahlweise.MONATLICH, "2026-11-10", "2024-09-10", null, Verlaengerung.ZAHLUNGSINTERVALL, 0);
			assertThat(berechnung.kuendigung(streaming)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-09"), d("2026-11-09"), 0, false));
		}

		@Test
		void jaehrlichesZahlungsintervallIstNichtMonatlichKuendbar() {
			var jahresAbo = mit(Zahlweise.JAEHRLICH, "2027-03-01", null, null, Verlaengerung.ZAHLUNGSINTERVALL, 1);
			assertThat(berechnung.kuendigung(jahresAbo)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-01-31"), d("2027-02-28"), 1, false));
		}

		@Test
		void zwoelfMonateOhneLaufzeitendeAbBeginn() {
			var vertrag = mit(Zahlweise.JAEHRLICH, null, "2024-05-15", null, Verlaengerung.ZWOELF_MONATE, 3);
			var kuendigung = berechnung.kuendigung(vertrag);
			assertThat(kuendigung.art()).isEqualTo(KUENDIGEN_BIS);
			assertThat(kuendigung.zum()).isEqualTo(d("2027-05-14"));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-02-14"));

			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, null, null, Verlaengerung.ZWOELF_MONATE, 3))
					.art()).isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void ohneVerlaengerungsangabeNurDasLaufzeitende() {
			var vertrag = mit(Zahlweise.MONATLICH, null, null, "2027-06-30", null, 3);
			assertThat(berechnung.kuendigung(vertrag)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-03-31"), d("2027-06-30"), 3, false));

			// Frist schon verpasst: wie es danach weitergeht, ist unbekannt.
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, "2026-11-15", null, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, null, null, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void ohneFristGiltDasEnde() {
			var vertrag = mit(Zahlweise.JAEHRLICH, null, null, "2026-12-31", Verlaengerung.ZWOELF_MONATE, null);
			var kuendigung = berechnung.kuendigung(vertrag);
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2026-12-31"));
			assertThat(kuendigung.fristMonate()).isNull();
		}

		@Test
		void dringendAbFuenfundvierzigTagen() {
			// 01.11. + 45 Tage = 16.12.
			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, null, "2026-12-16", Verlaengerung.ZWOELF_MONATE, 0))
					.dringend()).isTrue();
			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, null, "2026-12-17", Verlaengerung.ZWOELF_MONATE, 0))
					.dringend()).isFalse();
		}

		@Test
		void heuteIstNochRechtzeitig() {
			var kuendigung = berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, null, "2026-12-01", Verlaengerung.ZWOELF_MONATE, 1));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(HEUTE);
			assertThat(kuendigung.zum()).isEqualTo(d("2026-12-01"));
		}

		@Test
		void endetOhneVerlaengerung() {
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, "2027-03-31", Verlaengerung.KEINE, 1)))
					.isEqualTo(new Kuendigung(ENDET, null, d("2027-03-31"),
							1, false));
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, null, Verlaengerung.KEINE, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void nichtKuendbar() {
			var rundfunk = new Vertragsdaten(Zahlweise.VIERTELJAEHRLICH, d("2026-11-15"), false, null, null, null, null,
					null);
			assertThat(berechnung.kuendigung(rundfunk).art()).isEqualTo(NICHT_KUENDBAR);
		}

		@Test
		void gekuendigtUndBeendet() {
			assertThat(berechnung.kuendigung(gekuendigtZum("2026-12-31"))).isEqualTo(
					new Kuendigung(GEKUENDIGT, null, d("2026-12-31"), 1,
							false));
			assertThat(berechnung.kuendigung(gekuendigtZum("2026-10-31")).art()).isEqualTo(BEENDET);
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, "2026-10-31", Verlaengerung.KEINE, 1))
					.art()).isEqualTo(BEENDET);
		}

	}

	@Nested
	class Datumsrechnung {

		@Test
		void kuendigenBisHaeltMonatsenden() {
			assertThat(Vertragsberechnung.kuendigenBis(d("2026-12-31"), 1)).isEqualTo(d("2026-11-30"));
			assertThat(Vertragsberechnung.kuendigenBis(d("2026-11-30"), 1)).isEqualTo(d("2026-10-31"));
			assertThat(Vertragsberechnung.kuendigenBis(d("2027-02-28"), 3)).isEqualTo(d("2026-11-30"));
			assertThat(Vertragsberechnung.kuendigenBis(d("2026-12-15"), 1)).isEqualTo(d("2026-11-15"));
			assertThat(Vertragsberechnung.kuendigenBis(d("2026-12-15"), 0)).isEqualTo(d("2026-12-15"));
		}

		@Test
		void verlaengerungImSchaltjahr() {
			assertThat(Vertragsberechnung.plusMonate(d("2027-02-28"), 12)).isEqualTo(d("2028-02-29"));
			assertThat(Vertragsberechnung.plusMonate(d("2028-02-29"), 12)).isEqualTo(d("2029-02-28"));
			assertThat(Vertragsberechnung.plusMonate(d("2026-01-31"), 1)).isEqualTo(d("2026-02-28"));
			assertThat(Vertragsberechnung.plusMonate(d("2026-04-30"), 1)).isEqualTo(d("2026-05-31"));
			assertThat(Vertragsberechnung.plusMonate(d("2026-01-30"), 1)).isEqualTo(d("2026-02-28"));
		}

	}

}
