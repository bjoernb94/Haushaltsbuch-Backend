package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import java.time.LocalDate;

/**
 * Rechnen mit Monaten, für Laufzeiten wie für die Termine der Preissteigerung. Weitergezählt wird immer vom
 * gespeicherten Datum aus, nicht Schritt für Schritt, damit Monatsenden nicht abrutschen (31.01. → 28.02. → 31.03.,
 * nicht 28.03.).
 */
public final class Monate {

	private Monate() {
	}

	/** Verschiebt um Monate und hält dabei ein Monatsende (28.02. + 12 Monate im Schaltjahr → 29.02.). */
	public static LocalDate plus(LocalDate datum, long monate) {
		LocalDate verschoben = datum.plusMonths(monate);
		return datum.getDayOfMonth() == datum.lengthOfMonth()
				? verschoben.withDayOfMonth(verschoben.lengthOfMonth())
				: verschoben;
	}

	/**
	 * Letzter Tag einer Laufzeit von {@code monate} Monaten ab {@code beginn}: der Tag vor dem Beginn-Tag im
	 * jeweiligen Monat; gibt es den Beginn-Tag dort nicht, der Monatsletzte (§ 188 Abs. 3 BGB). Beginn 15.03. → 14.04.,
	 * Beginn 31.01. → 28.02., 30.03., 30.04.
	 */
	public static LocalDate endeNach(LocalDate beginn, long monate) {
		LocalDate tag = beginn.plusMonths(monate);
		// Kürzer als der Beginn-Tag: plusMonths hat auf den Monatsletzten gekürzt, der ist dann das Ende.
		return tag.getDayOfMonth() < beginn.getDayOfMonth() ? tag : tag.minusDays(1);
	}

}
