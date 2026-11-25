package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Laufzeitberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
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
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class PreisberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Preisberechnung berechnung = new Preisberechnung(new Laufzeitberechnung(HEUTE));

	/** Prognose aus der Preissteigerung (#96); heute ist der 01.11.2026. */
	@Nested
	class Prognose {

		private static Preisliste preis(String wert) {
			return new Preisliste(List.of(new Preis(d("2025-11-15"), euro(wert))));
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
			var preise = new Preisliste(List.of(new Preis(d("2025-11-15"), euro("10.00")),
					new Preis(d("2027-02-01"), euro("12.00"))));
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

		/**
		 * Vergangene Termine nach dem letzten Eintrag sind fällig, die Prognose rechnet auf ihnen auf; ein Termin heute
		 * ist schon fällig, nicht Prognose (#159).
		 */
		@Test
		void aufDenFaelligenStufen() {
			assertThat(berechnung.prognose(vertrag(), preis("100.00"),
					prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2020-03-01", null)))
					.first().isEqualTo(new Prognosepreis(d("2027-03-01"), euro("121.00")));
			var heute = prozent("10", Erhoehungsrhythmus.JAEHRLICH, "2020-11-01", null);
			assertThat(berechnung.aktuellerPreis(vertrag(), preis("100.00"), heute)).isEqualTo(euro("110.00"));
			assertThat(berechnung.prognose(vertrag(), preis("100.00"), heute))
					.first().isEqualTo(new Prognosepreis(d("2027-11-01"), euro("121.00")));
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

		/** Beginnt der Vertrag erst später, erhöht die Prognose weder davor noch am Beginn (dort gilt sein erster Preis). */
		@Test
		void keineErhoehungBisZumBeginn() {
			var daten = mit(Zahlweise.MONATLICH, "2027-01-01", null, null, null);
			var preise = new Preisliste(List.of(new Preis(d("2027-01-01"), euro("10.00"))));
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-10-01");

			assertThat(berechnung.prognose(daten, preise, prognose)).first()
					.isEqualTo(new Prognosepreis(d("2027-04-01"), euro("11.00")));
		}

		@Test
		void preisverlaufMitAllenArten() {
			var daten = mit(Zahlweise.MONATLICH, "2024-01-01", null, null, null);
			var preise = new Preisliste(List.of(new Preis(d("2024-01-01"), euro("10.00")),
					new Preis(d("2025-06-01"), euro("11.00")),
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

		/** Nach dem Vertragsende nichts. */
		@Test
		void preisverlaufBisZumVertragsende() {
			var preise = new Preisliste(List.of(new Preis(d("2025-11-15"), euro("10.00")),
					new Preis(d("2027-06-01"), euro("11.00"))));

			assertThat(berechnung.preisverlauf(vertrag(), preise, KEINE)).extracting(Preispunkt::ab)
					.containsExactly(d("2025-11-15"), d("2027-06-01"));
			assertThat(berechnung.preisverlauf(gekuendigtZum("2027-05-31"), preise, KEINE)).extracting(Preispunkt::art)
					.containsExactly(Preispunktart.GEZAHLT);
		}

	}

	/** Ein beendeter oder stornierter Vertrag hat keine Preisänderung mehr. */
	@Nested
	class NachDemEnde {

		@Test
		void keinePreisaenderungBeiBeendetemVertrag() {
			var preise = new Preisliste(List.of(new Preis(d("2025-11-15"), euro("10.00")),
					new Preis(d("2027-01-01"), euro("12.00"))));

			assertThat(berechnung.naechstePreisaenderung(vertrag(), preise, KEINE)).isPresent();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-12-31"), preise, KEINE)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigtZum("2026-10-31"), preise, KEINE)).isEmpty();
			assertThat(berechnung.naechstePreisaenderung(gekuendigt("2027-02-01", "2027-01-31"), preise, KEINE)).isEmpty();
		}

	}

}
