package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;

class EinmalkostenTests {

	private final List<Einmalkosten> eintraege = List.of(
			new Einmalkosten(LocalDate.of(2025, 12, 31), euro("49.00")),
			new Einmalkosten(LocalDate.of(2026, 1, 1), euro("99.99")),
			new Einmalkosten(LocalDate.of(2026, 12, 31), euro("0.01")));

	@Test
	void summeAllerEintraege() {
		assertThat(Einmalkosten.summe(eintraege, Betrag.EUR)).isEqualTo(euro("149.00"));
		assertThat(Einmalkosten.summe(List.of(), Betrag.EUR)).isEqualTo(euro("0.00"));
	}

	@Test
	void imJahrNurDieDortFaelligen() {
		assertThat(Einmalkosten.imJahr(eintraege, 2026, Betrag.EUR)).isEqualTo(euro("100.00"));
		assertThat(Einmalkosten.imJahr(eintraege, 2027, Betrag.EUR)).isEqualTo(euro("0.00"));
	}

}
