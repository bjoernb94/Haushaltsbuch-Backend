package de.bochmann.haushaltsbuch.vertraege.berechnung.kosten;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import org.junit.jupiter.api.Test;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;

class KostenTests {

	@Test
	void proJahrExakt() {
		assertThat(Kosten.proJahr(euro("19.99"), Zahlweise.MONATLICH)).isEqualTo(euro("239.88"));
		assertThat(Kosten.proJahr(euro("18.75"), Zahlweise.VIERTELJAEHRLICH)).isEqualTo(euro("75.00"));
		assertThat(Kosten.proJahr(euro("60.00"), Zahlweise.HALBJAEHRLICH)).isEqualTo(euro("120.00"));
		assertThat(Kosten.proJahr(euro("586.80"), Zahlweise.JAEHRLICH)).isEqualTo(euro("586.80"));
	}

	@Test
	void proMonatAusDemJahrGerundet() {
		assertThat(Kosten.proMonat(euro("586.80"), Zahlweise.JAEHRLICH)).isEqualTo(euro("48.90"));
		assertThat(Kosten.proMonat(euro("55.08"), Zahlweise.VIERTELJAEHRLICH)).isEqualTo(euro("18.36"));
		assertThat(Kosten.proMonat(euro("10.00"), Zahlweise.VIERTELJAEHRLICH)).isEqualTo(euro("3.33"));
		assertThat(Kosten.proMonat(euro("19.99"), Zahlweise.MONATLICH)).isEqualTo(euro("19.99"));
	}

}
