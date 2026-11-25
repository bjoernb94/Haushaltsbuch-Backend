package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreislisteTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 11, 1);

	private static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	/** Ab Beginn 01.01.2026 10 €, ab 01.03.2026 12 €, angekündigt ab 01.01.2027 15 €; absichtlich unsortiert. */
	private final Preisliste preise = new Preisliste(List.of(
			new Preis(d("2027-01-01"), euro("15.00")),
			new Preis(d("2026-01-01"), euro("10.00")),
			new Preis(d("2026-03-01"), euro("12.00"))));

	@Test
	void sortiertNachGueltigAb() {
		assertThat(preise.preise()).extracting(Preis::gueltigAb)
				.containsExactly(d("2026-01-01"), d("2026-03-01"), d("2027-01-01"));
	}

	@Test
	void preisAmTagGiltAbGueltigAb() {
		assertThat(preise.am(d("2026-02-28"))).isEqualTo(euro("10.00"));
		assertThat(preise.am(d("2026-03-01"))).isEqualTo(euro("12.00"));
		assertThat(preise.am(HEUTE)).isEqualTo(euro("12.00"));
		assertThat(preise.am(d("2027-06-01"))).isEqualTo(euro("15.00"));
	}

	/** Vor dem ersten Eintrag gilt der erste, z.&nbsp;B. als aktueller Preis eines Vertrags, der erst später beginnt. */
	@Test
	void vorDemErstenEintragGiltDerErste() {
		var spaeter = new Preisliste(List.of(new Preis(d("2026-06-01"), euro("10.00")),
				new Preis(d("2026-09-01"), euro("11.00"))));

		assertThat(spaeter.am(d("2026-01-01"))).isEqualTo(euro("10.00"));
	}

	@Test
	void naechsteAenderungIstDerEintragNachDemHeutigen() {
		assertThat(preise.naechsteAenderung(HEUTE, Optional.empty()))
				.contains(new Preisaenderung(d("2027-01-01"), euro("15.00"), Preisaenderungsart.ANGEKUENDIGT));
		assertThat(preise.naechsteAenderung(d("2027-01-01"), Optional.empty())).isEmpty();
	}

	/** Beginnt der Vertrag erst in der Zukunft, ist der erste Eintrag schon der Betrag, keine Änderung. */
	@Test
	void ersterEintragInDerZukunftKeineAenderung() {
		var kuenftig = new Preisliste(List.of(new Preis(d("2027-01-01"), euro("10.00")),
				new Preis(d("2027-07-01"), euro("11.00"))));

		assertThat(kuenftig.am(HEUTE)).isEqualTo(euro("10.00"));
		assertThat(kuenftig.naechsteAenderung(HEUTE, Optional.empty())).map(Preisaenderung::ab).contains(d("2027-07-01"));
	}

	/** Eine Änderung nach dem Vertragsende ist unlogisch (#79) und zählt nicht; am letzten Tag schon. */
	@Test
	void aenderungNachDemEndeZaehltNicht() {
		assertThat(preise.naechsteAenderung(HEUTE, Optional.of(d("2026-12-31")))).isEmpty();
		assertThat(preise.naechsteAenderung(HEUTE, Optional.of(d("2027-01-01")))).isPresent();
	}

	@Test
	void summeJeAbbuchungMitIhremPreis() {
		assertThat(preise.summe(List.of(d("2026-02-01"), d("2026-03-01"), d("2027-01-01")))).isEqualTo(euro("37.00"));
		assertThat(preise.summe(List.of())).isEqualTo(euro("0.00"));
	}

	@Test
	void ohnePreiseUngueltig() {
		assertThatThrownBy(() -> new Preisliste(List.of())).isInstanceOf(IllegalArgumentException.class);
	}

}
