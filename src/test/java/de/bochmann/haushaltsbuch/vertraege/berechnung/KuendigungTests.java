package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KuendigungTests {

	private static Kuendigung kuendigenBis(LocalDate bis) {
		return new Kuendigung(Kuendigungsart.KUENDIGEN_BIS, bis, bis.plusMonths(1), 1, false);
	}

	private static Kuendigung mitZum(Kuendigungsart art, LocalDate zum) {
		return new Kuendigung(art, null, zum, null, false);
	}

	@Test
	void sortiertNachDringlichkeit() {
		var kfz = kuendigenBis(LocalDate.of(2026, 11, 30));
		var handy = mitZum(Kuendigungsart.ENDET, LocalDate.of(2026, 12, 15));
		var hausrat = kuendigenBis(LocalDate.of(2027, 8, 31));
		var fitness = new Kuendigung(Kuendigungsart.MONATLICH_KUENDBAR, LocalDate.of(2026, 11, 30),
				LocalDate.of(2026, 12, 31), 1, false);
		var streaming = new Kuendigung(Kuendigungsart.MONATLICH_KUENDBAR, LocalDate.of(2026, 11, 9),
				LocalDate.of(2026, 11, 9), 0, false);
		var keineLaufzeit = Kuendigung.ohneDaten(Kuendigungsart.KEINE_LAUFZEIT, null);
		var rundfunk = Kuendigung.ohneDaten(Kuendigungsart.NICHT_KUENDBAR, null);
		var spaeterGekuendigt = mitZum(Kuendigungsart.GEKUENDIGT, LocalDate.of(2027, 3, 31));
		var gekuendigt = mitZum(Kuendigungsart.GEKUENDIGT, LocalDate.of(2026, 12, 31));
		var beendet = Kuendigung.ohneDaten(Kuendigungsart.BEENDET, 1);
		var wiederAb = mitZum(Kuendigungsart.WIEDER_AB, LocalDate.of(2027, 1, 1));

		var sortiert = List.of(beendet, wiederAb, spaeterGekuendigt, gekuendigt, rundfunk, keineLaufzeit, fitness, streaming,
				hausrat, handy, kfz).stream().sorted(Kuendigung.NACH_DRINGLICHKEIT).toList();

		// ENDET steht nach Datum zwischen den Kündigungsfristen, nicht dahinter; unterbrochene (WIEDER_AB) zwischen
		// gekündigten und beendeten.
		assertThat(sortiert).containsExactly(kfz, handy, hausrat, streaming, fitness, keineLaufzeit, rundfunk, gekuendigt,
				spaeterGekuendigt, wiederAb, beendet);
	}

}
