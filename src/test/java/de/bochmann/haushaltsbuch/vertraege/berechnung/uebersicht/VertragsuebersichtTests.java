package de.bochmann.haushaltsbuch.vertraege.berechnung.uebersicht;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Einmalkosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.IntStream;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.d;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.gekuendigt;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.mit;
import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.vertrag;
import static org.assertj.core.api.Assertions.assertThat;

/** Stichtag ist der 01.11.2026; jeder Vertrag hat einen Preis ab seinem Beginn. */
class VertragsuebersichtTests {

	private static final int JAHR = 2026;

	private final Vertragsberechnung berechnung = new Vertragsberechnung(LocalDate.of(JAHR, 11, 1));

	private static Betrag franken(String wert) {
		return new Betrag(new BigDecimal(wert), Currency.getInstance("CHF"));
	}

	private Vertragsuebersicht.Eintrag<String> eintrag(String name, Vertragsdaten daten, Betrag preis, Anteil anteil,
			List<Einmalkosten> einmalkosten) {
		var grundlage = new Berechnungsgrundlage(daten, new Preisliste(List.of(new Preis(daten.beginn(), preis))),
				Preisprognose.KEINE, anteil, einmalkosten);
		return new Vertragsuebersicht.Eintrag<>(name, grundlage, berechnung.werte(grundlage));
	}

	private Vertragsuebersicht.Eintrag<String> eintrag(String name, Vertragsdaten daten, Betrag preis) {
		return eintrag(name, daten, preis, Anteil.GANZ, List.of());
	}

	/**
	 * Laufende zählen pro Monat, im Jahr auch noch nicht begonnene und beendete mit Abbuchungen im Jahr; Einmalkosten
	 * auch von beendeten. Je Währung, nach Code sortiert.
	 */
	@Test
	void summenJeWaehrung() {
		var vertraege = List.of(
				// 12 Abbuchungen zu 10 €
				eintrag("laufend", vertrag(), euro("10.00")),
				// 12 Abbuchungen zu 5 CHF
				eintrag("franken", mit(Zahlweise.MONATLICH, "2026-01-01", null, null, null), franken("5.00")),
				// beginnt am 01.12.: eine Abbuchung im Jahr, zählt nicht pro Monat
				eintrag("spaeter", mit(Zahlweise.MONATLICH, "2026-12-01", null, null, null), euro("20.00")),
				// beendet zum 31.03.: drei Abbuchungen, dazu Einmalkosten im Februar
				eintrag("beendet", gekuendigt("2025-11-15", "2026-03-31"), euro("10.00"), Anteil.GANZ,
						List.of(new Einmalkosten(d("2026-02-01"), euro("25.00")),
								new Einmalkosten(d("2025-12-01"), euro("99.00")))));

		var uebersicht = Vertragsuebersicht.aus(vertraege, JAHR);

		assertThat(uebersicht.laufendeVertraege()).isEqualTo(2);
		assertThat(uebersicht.kostenProMonat()).containsExactly(franken("5.00"), euro("10.00"));
		assertThat(uebersicht.kostenImJahr()).containsExactly(franken("60.00"), euro("170.00"));
		assertThat(uebersicht.einmalkostenImJahr()).containsExactly(euro("25.00"));
		assertThat(uebersicht.kostenImJahrMitPrognose()).isFalse();
		assertThat(uebersicht.hatAnteile()).isFalse();
		assertThat(uebersicht.hatNichtBegonnene()).isTrue();
	}

	/** Geteilte Verträge zählen nur mit dem eigenen Anteil, auch bei den Einmalkosten. */
	@Test
	void nurDerEigeneAnteil() {
		var haelfte = Anteil.aus(new BigDecimal("50"));
		var vertraege = List.of(eintrag("geteilt", vertrag(), euro("10.00"), haelfte,
				List.of(new Einmalkosten(d("2026-03-01"), euro("25.00")))));

		var uebersicht = Vertragsuebersicht.aus(vertraege, JAHR);

		assertThat(uebersicht.kostenProMonat()).containsExactly(euro("5.00"));
		assertThat(uebersicht.kostenImJahr()).containsExactly(euro("60.00"));
		assertThat(uebersicht.einmalkostenImJahr()).containsExactly(euro("12.50"));
		assertThat(uebersicht.hatAnteile()).isTrue();
		assertThat(uebersicht.hatNichtBegonnene()).isFalse();
	}

	/** Die ersten fünf mit „kündigen bis“, in der Reihenfolge der Liste; andere Arten zählen nicht. */
	@Test
	void naechsteKuendigungsfristen() {
		List<Vertragsuebersicht.Eintrag<String>> vertraege = new ArrayList<>();
		vertraege.add(eintrag("ohneLaufzeit", vertrag(), euro("10.00")));
		IntStream.rangeClosed(1, 6).forEach(i -> vertraege.add(eintrag("frist" + i,
				mit(Zahlweise.MONATLICH, "2025-11-15", "2027-03-31", Verlaengerung.ZWOELF_MONATE, 1), euro("10.00"))));

		var uebersicht = Vertragsuebersicht.aus(vertraege, JAHR);

		assertThat(uebersicht.naechsteKuendigungsfristen())
				.containsExactly("frist1", "frist2", "frist3", "frist4", "frist5");
	}

	@Test
	void ohneVertraegeLeer() {
		var uebersicht = Vertragsuebersicht.<String>aus(List.of(), JAHR);

		assertThat(uebersicht).isEqualTo(new Vertragsuebersicht<String>(0, List.of(), List.of(), false, List.of(),
				false, false, List.of()));
	}

}
