package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.BEENDET;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.ENDET;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.GEKUENDIGT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.KEINE_LAUFZEIT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.KUENDIGEN_BIS;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.MONATLICH_KUENDBAR;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.NICHT_KUENDBAR;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart.STORNIERT;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.d;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.gekuendigt;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.gekuendigtZum;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.mit;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.vertrag;
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class LaufzeitberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Laufzeitberechnung berechnung = new Laufzeitberechnung(HEUTE);

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

	/** Letzter Vertragstag aus {@link Laufzeitberechnung#vertragsende}: Kündigung oder Laufzeitende ohne Verlängerung. */
	@Nested
	class Vertragsende {

		private LocalDate vertragsende(Vertragsdaten daten) {
			return berechnung.vertragsende(daten).orElse(null);
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

			assertThat(new Laufzeitberechnung(d("2026-10-15")).kuendigung(vertrag))
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

		@Test
		void kuendigenBisHaeltMonatsenden() {
			assertThat(Laufzeitberechnung.kuendigenBis(d("2026-12-31"), 1)).isEqualTo(d("2026-11-30"));
			assertThat(Laufzeitberechnung.kuendigenBis(d("2026-11-30"), 1)).isEqualTo(d("2026-10-31"));
			assertThat(Laufzeitberechnung.kuendigenBis(d("2027-02-28"), 3)).isEqualTo(d("2026-11-30"));
			assertThat(Laufzeitberechnung.kuendigenBis(d("2026-12-15"), 1)).isEqualTo(d("2026-11-15"));
			assertThat(Laufzeitberechnung.kuendigenBis(d("2026-12-15"), 0)).isEqualTo(d("2026-12-15"));
		}

	}

}
