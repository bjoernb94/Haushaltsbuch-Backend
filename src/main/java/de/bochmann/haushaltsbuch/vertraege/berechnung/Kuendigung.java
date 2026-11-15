package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Ergebnis von {@link Vertragsberechnung#kuendigung}.
 *
 * @param kuendigenBis letzter Tag, an dem die Kündigung zugehen muss; nur bei {@link Kuendigungsart#KUENDIGEN_BIS}
 *        und {@link Kuendigungsart#MONATLICH_KUENDBAR}
 * @param zum Tag, an dem der Vertrag (nach Kündigung) endet; leer, wenn es keinen gibt
 * @param fristMonate die hinterlegte Kündigungsfrist, leer wenn keine angegeben ist
 * @param dringend {@code kuendigenBis} ist höchstens {@value Vertragsberechnung#DRINGEND_TAGE} Tage entfernt
 */
public record Kuendigung(Kuendigungsart art, @Nullable LocalDate kuendigenBis, @Nullable LocalDate zum, @Nullable Integer fristMonate,
		boolean dringend) {

	/**
	 * Reihenfolge der Vertragsliste, die dringendste zuerst: {@code KUENDIGEN_BIS} und {@code ENDET}
	 * gemeinsam nach Datum, dann {@code MONATLICH_KUENDBAR}, {@code KEINE_LAUFZEIT}, {@code NICHT_KUENDBAR},
	 * {@code GEKUENDIGT} und {@code BEENDET} (mit {@code STORNIERT}); innerhalb nach Datum, soweit es eins gibt. Gleich dringende
	 * sortieren die Aufrufer weiter.
	 */
	public static final Comparator<Kuendigung> NACH_DRINGLICHKEIT = Comparator.comparingInt(Kuendigung::rang)
			.thenComparing(Kuendigung::stichtag, Comparator.nullsLast(Comparator.naturalOrder()));

	static Kuendigung ohneDaten(Kuendigungsart art, @Nullable Integer fristMonate) {
		return new Kuendigung(art, null, null, fristMonate, false);
	}

	private int rang() {
		return switch (art) {
			case KUENDIGEN_BIS, ENDET -> 0;
			case MONATLICH_KUENDBAR -> 1;
			case KEINE_LAUFZEIT -> 2;
			case NICHT_KUENDBAR -> 3;
			case GEKUENDIGT -> 4;
			case BEENDET, STORNIERT -> 5;
		};
	}

	/** Der Tag, bis zu dem etwas zu tun ist oder an dem der Vertrag endet. */
	private LocalDate stichtag() {
		return kuendigenBis != null ? kuendigenBis : zum;
	}

}
