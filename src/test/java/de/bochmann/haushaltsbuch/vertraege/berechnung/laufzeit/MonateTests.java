package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import org.junit.jupiter.api.Test;

import static de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Testvertraege.d;
import static org.assertj.core.api.Assertions.assertThat;

/** Rechnen mit Monaten: Monatsenden rutschen nicht ab. */
class MonateTests {

	/** Tag vor dem Beginn-Tag, sonst der Monatsletzte (§ 188 Abs. 3 BGB). */
	@Test
	void endeNachVomBeginnAus() {
		assertThat(Monate.endeNach(d("2026-03-15"), 1)).isEqualTo(d("2026-04-14"));
		assertThat(Monate.endeNach(d("2026-01-31"), 1)).isEqualTo(d("2026-02-28"));
		assertThat(Monate.endeNach(d("2026-01-31"), 2)).isEqualTo(d("2026-03-30"));
		assertThat(Monate.endeNach(d("2026-01-31"), 3)).isEqualTo(d("2026-04-30"));
		assertThat(Monate.endeNach(d("2026-03-01"), 1)).isEqualTo(d("2026-03-31"));
		assertThat(Monate.endeNach(d("2026-01-30"), 1)).isEqualTo(d("2026-02-28"));
		assertThat(Monate.endeNach(d("2028-01-29"), 1)).isEqualTo(d("2028-02-28"));
		assertThat(Monate.endeNach(d("2024-02-29"), 12)).isEqualTo(d("2025-02-28"));
	}

	@Test
	void verlaengerungImSchaltjahr() {
		assertThat(Monate.plus(d("2027-02-28"), 12)).isEqualTo(d("2028-02-29"));
		assertThat(Monate.plus(d("2028-02-29"), 12)).isEqualTo(d("2029-02-28"));
		assertThat(Monate.plus(d("2026-01-31"), 1)).isEqualTo(d("2026-02-28"));
		assertThat(Monate.plus(d("2026-04-30"), 1)).isEqualTo(d("2026-05-31"));
		assertThat(Monate.plus(d("2026-01-30"), 1)).isEqualTo(d("2026-02-28"));
	}

}
