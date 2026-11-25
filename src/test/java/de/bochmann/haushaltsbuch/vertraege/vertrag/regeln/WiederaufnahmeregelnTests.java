package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Vertragsende 31.05.2026; am 01.06. ist ein Preis eingetragen, am 01.07. eine automatische Stufe. */
class WiederaufnahmeregelnTests {

	private static final LocalDate VERTRAGSENDE = LocalDate.of(2026, 5, 31);

	private static final Preisliste PREISE = new Preisliste(List.of(
			new Preis(LocalDate.of(2025, 1, 1), euro("10.00")),
			new Preis(LocalDate.of(2026, 6, 1), euro("12.00")),
			new Preis(LocalDate.of(2026, 7, 1), euro("12.50"), Preisherkunft.AUTOMATISCH)));

	private record Angabe(LocalDate datum, LocalDate laufzeitende, Verlaengerung verlaengerung, Betrag betrag)
			implements Wiederaufnahmeangabe {
	}

	private static Angabe am(String datum) {
		return new Angabe(LocalDate.parse(datum), null, Verlaengerung.MONATLICH, null);
	}

	private static void verstoss(Executable pruefung, String feld, String code) {
		var fehler = assertThrows(UngueltigeEingabeException.class, pruefung);
		assertThat(fehler.getFeld()).isEqualTo(feld);
		assertThat(fehler.getCode()).isEqualTo(code);
	}

	@Test
	void nachDemVertragsende() {
		var fehler = assertThrows(UngueltigeEingabeException.class,
				() -> Wiederaufnahmeregeln.pruefe(am("2026-05-31"), true, PREISE, VERTRAGSENDE));

		assertThat(fehler.getFeld()).isEqualTo("datum");
		assertThat(fehler.getCode()).isEqualTo("NachVertragsende");
		assertThat(fehler.getParameter()).isEqualTo(Map.of("vertragsende", VERTRAGSENDE));
		assertThatCode(() -> Wiederaufnahmeregeln.pruefe(am("2026-06-01"), true, PREISE, VERTRAGSENDE))
				.doesNotThrowAnyException();
	}

	/** Kündbare brauchen eine Verlängerung und ohne sie ein Laufzeitende; nicht kündbare ignorieren beides. */
	@Test
	void verlaengerungBeiKuendbaren() {
		var ohne = new Angabe(LocalDate.of(2026, 6, 1), null, null, null);
		var keine = new Angabe(LocalDate.of(2026, 6, 1), null, Verlaengerung.KEINE, null);

		verstoss(() -> Wiederaufnahmeregeln.pruefe(ohne, true, PREISE, VERTRAGSENDE), "verlaengerung", "NotNull");
		verstoss(() -> Wiederaufnahmeregeln.pruefe(keine, true, PREISE, VERTRAGSENDE), "verlaengerung",
				"LaufzeitendeBeiEnde");
		assertThatCode(() -> Wiederaufnahmeregeln.pruefe(ohne, false, PREISE, VERTRAGSENDE)).doesNotThrowAnyException();
		assertThatCode(() -> Wiederaufnahmeregeln.pruefe(keine, false, PREISE, VERTRAGSENDE)).doesNotThrowAnyException();
	}

	@Test
	void laufzeitendeNichtVorDemDatum() {
		var angabe = new Angabe(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31), Verlaengerung.MONATLICH, null);

		verstoss(() -> Wiederaufnahmeregeln.pruefe(angabe, true, PREISE, VERTRAGSENDE), "laufzeitende",
				"LaufzeitendeNachBeginn");
	}

	@Test
	void betragInDerWaehrungDerPreise() {
		var franken = new Betrag(new BigDecimal("12.00"), Currency.getInstance("CHF"));
		var angabe = new Angabe(LocalDate.of(2026, 6, 15), null, Verlaengerung.MONATLICH, franken);

		verstoss(() -> Wiederaufnahmeregeln.pruefe(angabe, true, PREISE, VERTRAGSENDE), "betrag.waehrung",
				"GleicheWaehrung");
	}

	/** Ein eingetragener Preis am Tag bleibt, eine automatische Stufe ersetzt der neue Betrag. */
	@Test
	void keinZweiterEingetragenerPreisAmTag() {
		var eingetragen = new Angabe(LocalDate.of(2026, 6, 1), null, Verlaengerung.MONATLICH, euro("13.00"));
		var automatisch = new Angabe(LocalDate.of(2026, 7, 1), null, Verlaengerung.MONATLICH, euro("13.00"));

		verstoss(() -> Wiederaufnahmeregeln.pruefe(eingetragen, true, PREISE, VERTRAGSENDE), "betrag",
				"GueltigAbEindeutig");
		assertThatCode(() -> Wiederaufnahmeregeln.pruefe(automatisch, true, PREISE, VERTRAGSENDE))
				.doesNotThrowAnyException();
	}

}
