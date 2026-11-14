package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
import static org.assertj.core.api.Assertions.assertThat;

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
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.MONATLICH, "2026-11-01", null, null, null, null), preise))
					.isEqualTo(euro("180.00"));
			// Jährlich im Oktober: nur der neue Preis.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.JAEHRLICH, "2026-10-01", null, null, null, null), preise))
					.isEqualTo(euro("20.00"));
		}

		@Test
		void naechsteAbbuchungMitPreisDesTages() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2026-11-15"), euro("12.00"))));

			assertThat(berechnung.naechsteAbbuchung(vertrag(), preise)).contains(new Abbuchung(d("2026-11-15"), euro("12.00")));
		}

		@Test
		void keinePreisaenderungBeiBeendetemVertrag() {
			var preise = new Preisliste(List.of(new Preis(null, euro("10.00")), new Preis(d("2027-01-01"), euro("12.00"))));

			assertThat(berechnung.naechstePreisaenderung(vertrag(), preise)).isPresent();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-12-31"), preise)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-10-31"), preise)).isEmpty();
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
