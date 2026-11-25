package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import java.time.LocalDate;

/** Vertragsdaten für die Tests der Berechnung; die Tests setzen nur, was sie brauchen. */
public final class Testvertraege {

	private Testvertraege() {
	}

	public static LocalDate d(String iso) {
		return LocalDate.parse(iso);
	}

	/** Monatlich ab 15.11.2025 (Abbuchung am 15.), kündbar, sonst leer. */
	public static Vertragsdaten vertrag() {
		return mit(Zahlweise.MONATLICH, "2025-11-15", null, null, null);
	}

	public static Vertragsdaten mit(Zahlweise zahlweise, String beginn, String laufzeitende,
			Verlaengerung verlaengerung, Integer frist) {
		return new Vertragsdaten(zahlweise, true, beginn == null ? null : d(beginn),
				laufzeitende == null ? null : d(laufzeitende), verlaengerung, frist, null);
	}

	/** Wie {@link #vertrag()}, gekündigt zum {@code datum}. */
	public static Vertragsdaten gekuendigtZum(String datum) {
		return gekuendigt("2025-11-15", datum);
	}

	public static Vertragsdaten gekuendigt(String beginn, String gekuendigtZum) {
		return new Vertragsdaten(Zahlweise.MONATLICH, true, d(beginn), null, null, 1, d(gekuendigtZum));
	}

}
