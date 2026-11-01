package de.bochmann.haushaltsbuch.vertraege.berechnung;

import java.time.LocalDate;

/**
 * Ergebnis von {@link Vertragsberechnung#kuendigung}.
 *
 * @param kuendigenBis letzter Tag, an dem die Kündigung zugehen muss; nur bei {@link Kuendigungsart#KUENDIGEN_BIS}
 *        und {@link Kuendigungsart#MONATLICH_KUENDBAR}
 * @param zum Tag, an dem der Vertrag (nach Kündigung) endet; leer, wenn es keinen gibt
 * @param fristMonate die hinterlegte Kündigungsfrist, leer wenn keine angegeben ist
 * @param dringend {@code kuendigenBis} ist höchstens {@value Vertragsberechnung#DRINGEND_TAGE} Tage entfernt
 */
public record Kuendigung(Kuendigungsart art, LocalDate kuendigenBis, LocalDate zum, Integer fristMonate,
		boolean dringend) {

	static Kuendigung ohneDaten(Kuendigungsart art, Integer fristMonate) {
		return new Kuendigung(art, null, null, fristMonate, false);
	}

}
