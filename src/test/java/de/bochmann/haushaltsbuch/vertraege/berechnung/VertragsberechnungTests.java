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
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart.STORNIERT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preisprognose.KEINE;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart.AKTUELL;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart.ANGEKUENDIGT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart.FRUEHER;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart.NACH_ENDE;
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class VertragsberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Vertragsberechnung berechnung = new Vertragsberechnung(HEUTE);

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	/** Monatlich ab 15.11.2025 (Abbuchung am 15.), kündbar, sonst leer; die Tests setzen nur, was sie brauchen. */
	private static Vertragsdaten vertrag() {
		return mit(Zahlweise.MONATLICH, "2025-11-15", null, null, null);
	}

	private static Vertragsdaten mit(Zahlweise zahlweise, String beginn, String laufzeitende,
			Verlaengerung verlaengerung, Integer frist) {
		return new Vertragsdaten(zahlweise, true, beginn == null ? null : d(beginn),
				laufzeitende == null ? null : d(laufzeitende), verlaengerung, frist, null);
	}

	/** Wie {@link #vertrag()}, gekündigt zum {@code datum}. */
	private static Vertragsdaten gekuendigtZum(String datum) {
		return gekuendigt("2025-11-15", datum);
	}

	private static Vertragsdaten gekuendigt(String beginn, String gekuendigtZum) {
		return new Vertragsdaten(Zahlweise.MONATLICH, true, d(beginn), null, null, 1, d(gekuendigtZum));
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
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, "2026-10-31", Verlaengerung.KEINE, null)))
					.isEqualTo(Vertragsstatus.BEENDET);
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, "2026-11-01", Verlaengerung.KEINE, null)))
					.isEqualTo(Vertragsstatus.AKTIV);
		}

		/** Bis zum Beginn noch nicht begonnen, auch wenn schon gekündigt; am Beginn läuft er. */
		@Test
		void nochNichtBegonnenBisZumBeginn() {
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, "2026-11-02", null, null, null)))
					.isEqualTo(Vertragsstatus.NICHT_BEGONNEN);
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, "2026-11-01", null, null, null)))
					.isEqualTo(Vertragsstatus.AKTIV);
			assertThat(berechnung.status(gekuendigt("2026-12-01", "2027-03-31"))).isEqualTo(Vertragsstatus.NICHT_BEGONNEN);
			// Zum Beginn gekündigt: läuft genau einen Tag.
			assertThat(berechnung.status(gekuendigt("2026-11-01", "2026-11-01"))).isEqualTo(Vertragsstatus.GEKUENDIGT);
		}

		/** Gekündigt zu einem Tag vor dem Beginn: läuft nie, vor wie nach dem Beginn. */
		@Test
		void storniertVorDemBeginn() {
			assertThat(berechnung.status(gekuendigt("2026-12-01", "2026-11-30"))).isEqualTo(Vertragsstatus.STORNIERT);
			assertThat(berechnung.status(gekuendigt("2026-06-01", "2026-05-31"))).isEqualTo(Vertragsstatus.STORNIERT);
		}

		@Test
		void mitVerlaengerungLaeuftNachDemLaufzeitendeWeiter() {
			assertThat(berechnung.status(mit(Zahlweise.MONATLICH, null, "2025-10-31", Verlaengerung.MONATLICH, 1)))
					.isEqualTo(Vertragsstatus.AKTIV);
		}

	}

	/** Letzter Vertragstag in {@link Vertragsberechnung#werte}: Kündigung oder Laufzeitende ohne Verlängerung. */
	@Nested
	class Vertragsende {

		private LocalDate vertragsende(Vertragsdaten daten) {
			return berechnung.werte(new Berechnungsgrundlage(daten, null, KEINE, Anteil.GANZ, List.of())).vertragsende();
		}

		@Test
		void kuendigungOderLaufzeitendeOhneVerlaengerung() {
			assertThat(vertragsende(gekuendigtZum("2026-10-31"))).isEqualTo(d("2026-10-31"));
			assertThat(vertragsende(mit(Zahlweise.MONATLICH, "2025-11-15", "2027-03-31", Verlaengerung.KEINE, null)))
					.isEqualTo(d("2027-03-31"));
			assertThat(vertragsende(gekuendigt("2026-12-01", "2026-11-30"))).isEqualTo(d("2026-11-30"));
		}

		/** Gekündigt zu einem Tag nach dem Laufzeitende ohne Verlängerung: Er endet schon mit der Laufzeit. */
		@Test
		void laufzeitendeVorDerKuendigung() {
			var vertrag = new Vertragsdaten(Zahlweise.MONATLICH, true, d("2025-11-15"), d("2027-03-31"),
					Verlaengerung.KEINE, null, d("2027-06-30"));

			assertThat(vertragsende(vertrag)).isEqualTo(d("2027-03-31"));
			// Umgekehrt zählt die frühere Kündigung.
			assertThat(vertragsende(new Vertragsdaten(Zahlweise.MONATLICH, true, d("2025-11-15"), d("2027-03-31"),
					Verlaengerung.KEINE, null, d("2026-12-31")))).isEqualTo(d("2026-12-31"));
		}

		@Test
		void offenesEndeLeer() {
			assertThat(vertragsende(vertrag())).isNull();
			assertThat(vertragsende(mit(Zahlweise.MONATLICH, "2025-11-15", "2027-03-31", Verlaengerung.MONATLICH, 1)))
					.isNull();
		}

	}

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
			assertThat(werte.preise()).isEqualTo(berechnung.preiszeilen(daten, preise));
			assertThat(werte.preise()).filteredOn(zeile -> zeile.art() == AKTUELL).singleElement()
					.isEqualTo(new Preiszeile(gueltigAb == null ? null : d(gueltigAb), euro(betrag), AKTUELL));
			assertThat(werte.preise()).extracting(Preiszeile::art).containsExactly(arten);
		}

		@Test
		void preiswechselHeute() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-11-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-11-01", "12.00", FRUEHER, AKTUELL);
		}

		@Test
		void angekuendigterPreisIstNichtAktuell() {
			var preise = preise(new Preis(d("2027-01-01"), euro("15.00")), new Preis(null, euro("10.00")),
					new Preis(d("2026-03-01"), euro("12.00")));

			aktuellIst(vertrag(), preise, "2026-03-01", "12.00", FRUEHER, AKTUELL, ANGEKUENDIGT);
			assertThat(berechnung.preiszeilen(vertrag(), preise)).extracting(Preiszeile::gueltigAb)
					.containsExactly(null, d("2026-03-01"), d("2027-01-01"));
		}

		@Test
		void kuenftigerBeginnDerErste() {
			var daten = mit(Zahlweise.MONATLICH, "2027-01-01", null, null, null);
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2027-07-01"), euro("11.00")));

			aktuellIst(daten, preise, null, "10.00", AKTUELL, ANGEKUENDIGT);
		}

		/** Ein Eintrag nach dem Ende wurde nie gezahlt; am letzten Tag gilt er schon. */
		@Test
		void beendetDerAmLetztenTagGueltige() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-07-01"), euro("12.00")),
					new Preis(d("2026-09-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2026-06-30"), preise, null, "10.00", AKTUELL, NACH_ENDE, NACH_ENDE);
			aktuellIst(gekuendigtZum("2026-07-01"), preise, "2026-07-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
			aktuellIst(gekuendigtZum("2026-10-31"), preise, "2026-09-01", "15.00", FRUEHER, FRUEHER, AKTUELL);
			aktuellIst(mit(Zahlweise.MONATLICH, null, "2026-08-31", Verlaengerung.KEINE, null), preise,
					"2026-07-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
		}

		@Test
		void beendetKostenProMonatMitDemAktuellenPreis() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-09-01"), euro("15.00")));

			assertThat(berechnung.werte(new Berechnungsgrundlage(gekuendigtZum("2026-06-30"), preise, KEINE, Anteil.GANZ,
					List.of())).kostenProMonat()).isEqualTo(euro("10.00"));
		}

		/**
		 * Ein gekündigter, noch laufender Vertrag hat den heute gültigen Preis; ein späterer ist angekündigt, wenn er
		 * spätestens am letzten Tag beginnt, sonst nach dem Ende.
		 */
		@Test
		void gekuendigtDerHeuteGueltige() {
			var preise = preise(new Preis(null, euro("10.00")), new Preis(d("2026-10-01"), euro("12.00")),
					new Preis(d("2027-03-01"), euro("15.00")));

			aktuellIst(gekuendigtZum("2027-03-01"), preise, "2026-10-01", "12.00", FRUEHER, AKTUELL, ANGEKUENDIGT);
			aktuellIst(gekuendigtZum("2027-02-28"), preise, "2026-10-01", "12.00", FRUEHER, AKTUELL, NACH_ENDE);
		}

	}

	@Nested
	class AbbuchungenImJahr {

		/** Ab einem Beginn vor dem Jahr alle Abbuchungen des Jahres im Takt ab Beginn. */
		@Test
		void beginnImVorjahrZwoelfImJahr() {
			assertThat(berechnung.abbuchungenImJahr(vertrag())).hasSize(12)
					.startsWith(d("2026-01-15"))
					.endsWith(d("2026-12-15"));
		}

		@Test
		void abDemBeginn() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2026-04-20", null, null, null)))
					.containsExactly(d("2026-04-20"), d("2026-05-20"), d("2026-06-20"), d("2026-07-20"), d("2026-08-20"),
							d("2026-09-20"), d("2026-10-20"), d("2026-11-20"), d("2026-12-20"));
		}

		/** Ein Vertrag ab 01.12. ist noch nicht begonnen, seine Abbuchung im Dezember zählt trotzdem. */
		@Test
		void nochNichtBegonnenZaehltImJahr() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2026-12-01", null, null, null)))
					.containsExactly(d("2026-12-01"));
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2027-01-01", null, null, null))).isEmpty();
		}

		@Test
		void nurBisZumEnde() {
			assertThat(berechnung.abbuchungenImJahr(gekuendigtZum("2026-03-15")))
					.containsExactly(d("2026-01-15"), d("2026-02-15"), d("2026-03-15"));
			var endet = mit(Zahlweise.VIERTELJAEHRLICH, "2025-11-01", "2026-06-30", Verlaengerung.KEINE, null);
			assertThat(berechnung.abbuchungenImJahr(endet)).containsExactly(d("2026-02-01"), d("2026-05-01"));
		}

		/** Vor dem Beginn gekündigt: keine Abbuchung; zum Beginn gekündigt: die eine am Beginn. */
		@Test
		void storniertKeine() {
			assertThat(berechnung.abbuchungenImJahr(gekuendigt("2026-12-01", "2026-11-30"))).isEmpty();
			assertThat(berechnung.abbuchungenImJahr(gekuendigt("2026-12-01", "2026-12-01"))).containsExactly(d("2026-12-01"));
		}

		@Test
		void jaehrlichEineAbbuchung() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.JAEHRLICH, "2020-07-01", null, null, null)))
					.containsExactly(d("2026-07-01"));
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.JAEHRLICH, "2025-03-01", null, null, null)))
					.containsExactly(d("2026-03-01"));
		}

		/** Vom Beginn aus gezählt, nicht Schritt für Schritt: 31.01. → 28.02. → 31.03. */
		@Test
		void monatsendeRutschtNichtAb() {
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, "2026-01-31", null, null, null)))
					.startsWith(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"));
		}

		@Test
		void beendetInEinemFruherenJahrKeine() {
			assertThat(berechnung.abbuchungenImJahr(gekuendigtZum("2025-12-31"))).isEmpty();
		}

		@Test
		void ohneAngabenKeine() {
			assertThat(berechnung.abbuchungenImJahr(mit(null, "2025-11-15", null, null, null))).isEmpty();
			assertThat(berechnung.abbuchungenImJahr(mit(Zahlweise.MONATLICH, null, null, null, null))).isEmpty();
		}

		@Test
		void kostenImJahrMitPreiswechsel() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2026-07-01"), euro("20.00"))));

			// Januar bis Juni zu 10 €, Juli bis Dezember zu 20 €.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.MONATLICH, "2025-12-01", null, null, null), preise, KEINE))
					.isEqualTo(euro("180.00"));
			// Jährlich im Oktober: nur der neue Preis.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.JAEHRLICH, "2025-10-01", null, null, null), preise, KEINE))
					.isEqualTo(euro("20.00"));
		}

		@Test
		void keinePreisaenderungBeiBeendetemVertrag() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-01-01"), euro("12.00"))));

			assertThat(berechnung.naechstePreisaenderung(vertrag(), preise, KEINE)).isPresent();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-12-31"), preise, KEINE)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-10-31"), preise, KEINE)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigt("2027-02-01", "2027-01-31"), preise, KEINE)).isEmpty();
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

		/** Storniert in der Zukunft: Der Termin vor dem Kündigungsdatum zählt nicht, der Vertrag läuft nie (#141). */
		@Test
		void storniertKeine() {
			var storniert = gekuendigt("2026-12-01", "2026-11-30");
			var prognose = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2026-11-15", null);

			assertThat(berechnung.prognose(storniert, preis("10.00"), prognose)).isEmpty();
			assertThat(berechnung.preisverlauf(storniert, preis("10.00"), prognose)).extracting(Preispunkt::art)
					.containsExactly(Preispunktart.GEZAHLT);
			assertThat(berechnung.naechstePreisaenderung(storniert, preis("10.00"), prognose)).isEmpty();
		}

		@Test
		void ohneRegelKeine() {
			assertThat(berechnung.prognose(vertrag(), preis("10.00"), KEINE)).isEmpty();
			assertThat(berechnung.prognose(vertrag(), preis("10.00"), new Preisprognose(null, d("2027-01-01")))).isEmpty();
		}

		/** Monatlich am 15.: Januar bis November zu 10 €, Dezember zur Prognose 11 €. */
		@Test
		void kostenImJahrZaehltDiePrognose() {
			var daten = mit(Zahlweise.MONATLICH, "2025-12-15", null, null, null);
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
			var daten = mit(Zahlweise.MONATLICH, "2025-12-15", null, null, null);
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-01-01");

			// Je drei Abbuchungen zu 11, 12, 13 und 14 €.
			assertThat(januar.kostenImJahr(daten, preis("10.00"), prognose)).isEqualTo(euro("150.00"));
			assertThat(januar.prognose(daten, preis("10.00"), prognose)).hasSize(3);
		}

		@Test
		void preisverlaufMitAllenArten() {
			var daten = mit(Zahlweise.MONATLICH, "2024-01-01", null, null, null);
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

		/** Der Eintrag ohne {@code gueltigAb} beginnt mit dem Vertrag; nach dem Vertragsende nichts. */
		@Test
		void preisverlaufStartdatumUndVertragsende() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-06-01"), euro("11.00"))));

			assertThat(berechnung.preisverlauf(vertrag(), preise, KEINE)).extracting(Preispunkt::ab)
					.containsExactly(d("2025-11-15"), d("2027-06-01"));
			assertThat(berechnung.preisverlauf(gekuendigtZum("2027-05-31"), preise, KEINE)).extracting(Preispunkt::art)
					.containsExactly(Preispunktart.GEZAHLT);
		}

	}

	@Nested
	class Kuendigungen {

		@Test
		void kfzVersicherungMitZwoelfMonatenVerlaengerung() {
			var kfz = mit(Zahlweise.JAEHRLICH, "2023-01-01", "2023-12-31", Verlaengerung.ZWOELF_MONATE, 1);
			assertThat(berechnung.kuendigung(kfz)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2026-11-30"), d("2026-12-31"), 1, true));
		}

		@Test
		void verlaengertWeiterWennDieFristVerpasstIst() {
			// Ende 30.09.2026 mit 3 Monaten Frist wäre bis 30.06.2026 zu kündigen gewesen.
			var haftpflicht = mit(Zahlweise.VIERTELJAEHRLICH, "2020-10-01", "2021-09-30",
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
			var hausrat = mit(Zahlweise.JAEHRLICH, "2021-07-01", "2022-06-30", Verlaengerung.ZWOELF_MONATE, 3);
			var kuendigung = berechnung.kuendigung(hausrat);
			assertThat(kuendigung.zum()).isEqualTo(d("2027-06-30"));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-03-31"));
		}

		@Test
		void vorDemLaufzeitendeKuendigenBisAuchBeiMonatlicherVerlaengerung() {
			var mobilfunk = mit(Zahlweise.MONATLICH, "2025-03-15", "2027-03-14", Verlaengerung.MONATLICH, 1);
			assertThat(berechnung.kuendigung(mobilfunk)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-02-14"), d("2027-03-14"), 1, false));
		}

		@Test
		void nachDemLaufzeitendeMonatlichKuendbar() {
			var mobilfunk = mit(Zahlweise.MONATLICH, "2024-03-15", "2026-03-14", Verlaengerung.MONATLICH, 1);
			assertThat(berechnung.kuendigung(mobilfunk)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-14"), d("2026-12-14"), 1, false));
		}

		@Test
		void ohneLaufzeitendeZumEndeDesZahlungsintervalls() {
			// Beginn am 1.: Ende ist jeweils der Monatsletzte (31.10., 30.11., …), 3 Monate Frist.
			var fitness = mit(Zahlweise.MONATLICH, "2025-05-01", null, Verlaengerung.ZAHLUNGSINTERVALL, 3);
			assertThat(berechnung.kuendigung(fitness)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-30"), d("2027-02-28"), 3, false));

			var streaming = mit(Zahlweise.MONATLICH, "2024-09-10", null, Verlaengerung.ZAHLUNGSINTERVALL, 0);
			assertThat(berechnung.kuendigung(streaming)).isEqualTo(
					new Kuendigung(MONATLICH_KUENDBAR, d("2026-11-09"), d("2026-11-09"), 0, false));
		}

		@Test
		void jaehrlichesZahlungsintervallIstNichtMonatlichKuendbar() {
			var jahresAbo = mit(Zahlweise.JAEHRLICH, "2025-03-01", null, Verlaengerung.ZAHLUNGSINTERVALL, 1);
			assertThat(berechnung.kuendigung(jahresAbo)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-01-31"), d("2027-02-28"), 1, false));
		}

		@Test
		void zwoelfMonateOhneLaufzeitendeAbBeginn() {
			var vertrag = mit(Zahlweise.JAEHRLICH, "2024-05-15", null, Verlaengerung.ZWOELF_MONATE, 3);
			var kuendigung = berechnung.kuendigung(vertrag);
			assertThat(kuendigung.art()).isEqualTo(KUENDIGEN_BIS);
			assertThat(kuendigung.zum()).isEqualTo(d("2027-05-14"));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2027-02-14"));

			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, null, Verlaengerung.ZWOELF_MONATE, 3))
					.art()).isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void ohneVerlaengerungsangabeNurDasLaufzeitende() {
			var vertrag = mit(Zahlweise.MONATLICH, null, "2027-06-30", null, 3);
			assertThat(berechnung.kuendigung(vertrag)).isEqualTo(
					new Kuendigung(KUENDIGEN_BIS, d("2027-03-31"), d("2027-06-30"), 3, false));

			// Frist schon verpasst: wie es danach weitergeht, ist unbekannt.
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, "2026-11-15", null, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, null, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void ohneFristGiltDasEnde() {
			var vertrag = mit(Zahlweise.JAEHRLICH, null, "2026-12-31", Verlaengerung.ZWOELF_MONATE, null);
			var kuendigung = berechnung.kuendigung(vertrag);
			assertThat(kuendigung.kuendigenBis()).isEqualTo(d("2026-12-31"));
			assertThat(kuendigung.fristMonate()).isNull();
		}

		@Test
		void dringendAbFuenfundvierzigTagen() {
			// 01.11. + 45 Tage = 16.12.
			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, "2026-12-16", Verlaengerung.ZWOELF_MONATE, 0))
					.dringend()).isTrue();
			assertThat(berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, "2026-12-17", Verlaengerung.ZWOELF_MONATE, 0))
					.dringend()).isFalse();
		}

		@Test
		void heuteIstNochRechtzeitig() {
			var kuendigung = berechnung.kuendigung(mit(Zahlweise.JAEHRLICH, null, "2026-12-01", Verlaengerung.ZWOELF_MONATE, 1));
			assertThat(kuendigung.kuendigenBis()).isEqualTo(HEUTE);
			assertThat(kuendigung.zum()).isEqualTo(d("2026-12-01"));
		}

		@Test
		void endetOhneVerlaengerung() {
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, "2027-03-31", Verlaengerung.KEINE, 1)))
					.isEqualTo(new Kuendigung(ENDET, null, d("2027-03-31"),
							1, false));
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, null, Verlaengerung.KEINE, 1)).art())
					.isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void nichtKuendbar() {
			var rundfunk = new Vertragsdaten(Zahlweise.VIERTELJAEHRLICH, false, d("2025-11-15"), null, null, null, null);
			assertThat(berechnung.kuendigung(rundfunk).art()).isEqualTo(NICHT_KUENDBAR);
		}

		@Test
		void gekuendigtUndBeendet() {
			assertThat(berechnung.kuendigung(gekuendigtZum("2026-12-31"))).isEqualTo(
					new Kuendigung(GEKUENDIGT, null, d("2026-12-31"), 1,
							false));
			assertThat(berechnung.kuendigung(gekuendigtZum("2026-10-31")).art()).isEqualTo(BEENDET);
			assertThat(berechnung.kuendigung(mit(Zahlweise.MONATLICH, null, "2026-10-31", Verlaengerung.KEINE, 1))
					.art()).isEqualTo(BEENDET);
		}

		/** Ohne Laufzeitende zählen alle Enden vom Beginn aus (§ 188 BGB): 31.01. monatlich → 30.10., nicht 31.10. */
		@Test
		void ohneLaufzeitendeVomBeginnAusGezaehlt() {
			var vertrag = mit(Zahlweise.MONATLICH, "2026-01-31", null, Verlaengerung.MONATLICH, 0);

			assertThat(new Vertragsberechnung(d("2026-10-15")).kuendigung(vertrag))
					.isEqualTo(new Kuendigung(MONATLICH_KUENDBAR, d("2026-10-30"), d("2026-10-30"), 0, false));
			assertThat(berechnung.kuendigung(vertrag).zum()).isEqualTo(d("2026-11-30"));
		}

		/** Ein noch nicht begonnener Vertrag ist schon kündbar; gekündigt bleibt er gekündigt. */
		@Test
		void nochNichtBegonnen() {
			var kfz = mit(Zahlweise.JAEHRLICH, "2026-12-01", null, Verlaengerung.ZWOELF_MONATE, 1);

			assertThat(berechnung.kuendigung(kfz))
					.isEqualTo(new Kuendigung(KUENDIGEN_BIS, d("2027-10-31"), d("2027-11-30"), 1, false));
			assertThat(berechnung.kuendigung(gekuendigt("2026-12-01", "2027-05-31")))
					.isEqualTo(new Kuendigung(GEKUENDIGT, null, d("2027-05-31"), 1, false));
		}

		/**
		 * Verlängerung um das Zahlungsintervall ohne Zahlweise (in der Vorschau möglich): Es gilt nur das Laufzeitende,
		 * ohne eins keine Laufzeit. Verlängerung {@code KEINE} erreicht {@code verlaengerungMonate} nicht, weil
		 * {@code kuendigung} vorher {@code ENDET} oder {@code KEINE_LAUFZEIT} liefert; der Zweig steht nur, weil der
		 * {@code switch} über das Enum vollständig sein muss, und wird bewusst nicht getestet.
		 */
		@Test
		void zahlungsintervallOhneZahlweise() {
			var mitLaufzeitende = new Vertragsdaten(null, true, d("2025-11-15"), d("2027-03-31"),
					Verlaengerung.ZAHLUNGSINTERVALL, 1, null);
			var ohneLaufzeitende = new Vertragsdaten(null, true, d("2025-11-15"), null,
					Verlaengerung.ZAHLUNGSINTERVALL, 1, null);

			assertThat(berechnung.kuendigung(mitLaufzeitende))
					.isEqualTo(new Kuendigung(KUENDIGEN_BIS, d("2027-02-28"), d("2027-03-31"), 1, false));
			assertThat(berechnung.kuendigung(ohneLaufzeitende).art()).isEqualTo(KEINE_LAUFZEIT);
		}

		@Test
		void storniert() {
			assertThat(berechnung.kuendigung(gekuendigt("2026-12-01", "2026-11-30")))
					.isEqualTo(new Kuendigung(STORNIERT, null, null, 1, false));
			assertThat(berechnung.kuendigung(gekuendigt("2026-06-01", "2026-05-31")).art()).isEqualTo(STORNIERT);
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

		/** Tag vor dem Beginn-Tag, sonst der Monatsletzte (§ 188 Abs. 3 BGB). */
		@Test
		void endeNachVomBeginnAus() {
			assertThat(Vertragsberechnung.endeNach(d("2026-03-15"), 1)).isEqualTo(d("2026-04-14"));
			assertThat(Vertragsberechnung.endeNach(d("2026-01-31"), 1)).isEqualTo(d("2026-02-28"));
			assertThat(Vertragsberechnung.endeNach(d("2026-01-31"), 2)).isEqualTo(d("2026-03-30"));
			assertThat(Vertragsberechnung.endeNach(d("2026-01-31"), 3)).isEqualTo(d("2026-04-30"));
			assertThat(Vertragsberechnung.endeNach(d("2026-03-01"), 1)).isEqualTo(d("2026-03-31"));
			assertThat(Vertragsberechnung.endeNach(d("2026-01-30"), 1)).isEqualTo(d("2026-02-28"));
			assertThat(Vertragsberechnung.endeNach(d("2028-01-29"), 1)).isEqualTo(d("2028-02-28"));
			assertThat(Vertragsberechnung.endeNach(d("2024-02-29"), 12)).isEqualTo(d("2025-02-28"));
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
