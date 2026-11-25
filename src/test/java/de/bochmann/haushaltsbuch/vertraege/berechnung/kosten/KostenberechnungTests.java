package de.bochmann.haushaltsbuch.vertraege.berechnung.kosten;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Laufzeitberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
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
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist immer der 01.11.2026; die Beispiele entsprechen den Verträgen im Design. */
class KostenberechnungTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private final Kostenberechnung berechnung = kosten(HEUTE);

	private static Kostenberechnung kosten(LocalDate heute) {
		var laufzeit = new Laufzeitberechnung(heute);
		return new Kostenberechnung(laufzeit, new Preisberechnung(laufzeit));
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
			var preise = new Preisliste(List.of(new Preis(d("2025-10-01"), euro("10.00")),
					new Preis(d("2026-07-01"), euro("20.00"))));

			// Januar bis Juni zu 10 €, Juli bis Dezember zu 20 €.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.MONATLICH, "2025-12-01", null, null, null), preise, KEINE))
					.isEqualTo(euro("180.00"));
			// Jährlich im Oktober: nur der neue Preis.
			assertThat(berechnung.kostenImJahr(mit(Zahlweise.JAEHRLICH, "2025-10-01", null, null, null), preise, KEINE))
					.isEqualTo(euro("20.00"));
		}

	}

	/** Kosten im Jahr mit den Preisen der Prognose (#96). */
	@Nested
	class MitPrognose {

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
			var januar = kosten(d("2026-01-01"));
			var daten = mit(Zahlweise.MONATLICH, "2025-12-15", null, null, null);
			var prognose = betrag("1.00", Erhoehungsrhythmus.VIERTELJAEHRLICH, "2026-01-01");

			// Je drei Abbuchungen zu 11, 12, 13 und 14 €.
			assertThat(januar.kostenImJahr(daten, preis("10.00"), prognose)).isEqualTo(euro("150.00"));
			assertThat(new Preisberechnung(new Laufzeitberechnung(d("2026-01-01"))).prognose(daten, preis("10.00"), prognose))
					.hasSize(3);
		}

	}

}
