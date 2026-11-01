package de.bochmann.haushaltsbuch.vertraege.berechnung;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Berechnete Angaben eines Vertrags zu einem Stichtag ({@code heute}): Status, nächste Abbuchung und
 * Kündigung. Ohne Spring und ohne Uhr, damit Tests jeden Tag festlegen können; die Aufrufer geben
 * {@code LocalDate.now(clock)} mit.
 * <p>
 * Weitergezählt wird immer vom gespeicherten Datum aus ({@code abbuchungAb}, Laufzeitende), nicht Schritt für Schritt, damit Monatsenden
 * nicht abrutschen (31.01. → 28.02. → 31.03., nicht 28.03.).
 */
public class Vertragsberechnung {

	/** Ab so vielen Tagen bis „kündigen bis“ gilt eine Kündigung als dringend. */
	public static final int DRINGEND_TAGE = 45;

	private final LocalDate heute;

	public Vertragsberechnung(LocalDate heute) {
		this.heute = heute;
	}

	/**
	 * {@link Vertragsstatus#BEENDET}, wenn das Kündigungsdatum vorbei ist oder ein Vertrag ohne
	 * Verlängerung sein Laufzeitende überschritten hat; {@link Vertragsstatus#GEKUENDIGT}, solange
	 * das Kündigungsdatum noch kommt; sonst {@link Vertragsstatus#AKTIV}.
	 */
	public Vertragsstatus status(Vertragsdaten vertrag) {
		if (vertrag.gekuendigtZum() != null) {
			return vertrag.gekuendigtZum().isBefore(heute) ? Vertragsstatus.BEENDET : Vertragsstatus.GEKUENDIGT;
		}
		if (vertrag.kuendbar() && vertrag.verlaengerung() == Verlaengerung.KEINE && vertrag.laufzeitende() != null
				&& vertrag.laufzeitende().isBefore(heute)) {
			return Vertragsstatus.BEENDET;
		}
		return Vertragsstatus.AKTIV;
	}

	/**
	 * Die erste Abbuchung ab heute (und nicht vor {@code abbuchungAb}) im Takt der Zahlweise; leer, wenn
	 * der Vertrag beendet ist, vorher endet (Kündigungsdatum) oder Angaben fehlen.
	 */
	public Optional<LocalDate> naechsteAbbuchung(Vertragsdaten vertrag) {
		if (vertrag.zahlweise() == null || vertrag.abbuchungAb() == null
				|| status(vertrag) == Vertragsstatus.BEENDET) {
			return Optional.empty();
		}
		int schritt = vertrag.zahlweise().monate();
		LocalDate anker = vertrag.abbuchungAb();
		long k = ersterSchritt(anker, schritt);
		while (anker.plusMonths(k * schritt).isBefore(heute)) {
			k++;
		}
		LocalDate abbuchung = anker.plusMonths(k * schritt);
		if (vertrag.gekuendigtZum() != null && abbuchung.isAfter(vertrag.gekuendigtZum())) {
			return Optional.empty();
		}
		return Optional.of(abbuchung);
	}

	/** Art und Termine der Kündigung; die Regeln stehen in {@code docs/api.md}, Abschnitt Verträge. */
	public Kuendigung kuendigung(Vertragsdaten vertrag) {
		Integer frist = vertrag.kuendigungsfristMonate();
		switch (status(vertrag)) {
			case BEENDET -> {
				return Kuendigung.ohneDaten(Kuendigungsart.BEENDET, frist);
			}
			case GEKUENDIGT -> {
				return new Kuendigung(Kuendigungsart.GEKUENDIGT, null, vertrag.gekuendigtZum(), frist, false);
			}
			case AKTIV -> {
			}
		}
		if (!vertrag.kuendbar()) {
			return Kuendigung.ohneDaten(Kuendigungsart.NICHT_KUENDBAR, frist);
		}
		if (vertrag.verlaengerung() == Verlaengerung.KEINE) {
			// Ein vergangenes Laufzeitende ergibt schon oben BEENDET.
			return vertrag.laufzeitende() != null
					? new Kuendigung(Kuendigungsart.ENDET, null, vertrag.laufzeitende(), frist, false)
					: Kuendigung.ohneDaten(Kuendigungsart.KEINE_LAUFZEIT, frist);
		}

		LocalDate erstesEnde = erstesEnde(vertrag);
		int schritt = verlaengerungMonate(vertrag);
		int fristMonate = frist != null ? frist : 0;
		if (erstesEnde == null || (schritt == 0 && kuendigenBis(erstesEnde, fristMonate).isBefore(heute))) {
			return Kuendigung.ohneDaten(Kuendigungsart.KEINE_LAUFZEIT, frist);
		}

		LocalDate ende = erstesEnde;
		if (schritt > 0) {
			long k = ersterSchritt(erstesEnde, schritt);
			while (kuendigenBis(plusMonate(erstesEnde, k * schritt), fristMonate).isBefore(heute)) {
				k++;
			}
			ende = plusMonate(erstesEnde, k * schritt);
		}
		LocalDate kuendigenBis = kuendigenBis(ende, fristMonate);

		boolean laufzeitVorbei = vertrag.laufzeitende() == null || vertrag.laufzeitende().isBefore(heute);
		if (laufzeitVorbei && verlaengertUmEinenMonat(vertrag)) {
			return new Kuendigung(Kuendigungsart.MONATLICH_KUENDBAR, kuendigenBis, ende, frist, false);
		}
		boolean dringend = ChronoUnit.DAYS.between(heute, kuendigenBis) <= DRINGEND_TAGE;
		return new Kuendigung(Kuendigungsart.KUENDIGEN_BIS, kuendigenBis, ende, frist, dringend);
	}

	/**
	 * Das erste Vertragsende, ab dem verlängert wird: das Laufzeitende; ohne Laufzeitende bei
	 * Verlängerung um das Zahlungsintervall oder monatlich der Tag vor der Abbuchung, bei zwölf Monaten
	 * ein Jahr nach Beginn. Ohne Verlängerung zählt nur das Laufzeitende selbst.
	 */
	private static LocalDate erstesEnde(Vertragsdaten vertrag) {
		if (vertrag.laufzeitende() != null) {
			return vertrag.laufzeitende();
		}
		if (vertrag.verlaengerung() == null) {
			return null;
		}
		return switch (vertrag.verlaengerung()) {
			case ZAHLUNGSINTERVALL, MONATLICH -> vertrag.abbuchungAb() != null
					? vertrag.abbuchungAb().minusDays(1) : null;
			case ZWOELF_MONATE -> vertrag.beginn() != null ? vertrag.beginn().plusYears(1).minusDays(1) : null;
			case KEINE -> null;
		};
	}

	/** Um wie viele Monate sich der Vertrag jeweils verlängert; 0 ohne (bekannte) Verlängerung. */
	private static int verlaengerungMonate(Vertragsdaten vertrag) {
		if (vertrag.verlaengerung() == null) {
			return 0;
		}
		return switch (vertrag.verlaengerung()) {
			case ZAHLUNGSINTERVALL -> vertrag.zahlweise() != null ? vertrag.zahlweise().monate() : 0;
			case MONATLICH -> 1;
			case ZWOELF_MONATE -> 12;
			case KEINE -> 0;
		};
	}

	private static boolean verlaengertUmEinenMonat(Vertragsdaten vertrag) {
		return vertrag.verlaengerung() == Verlaengerung.MONATLICH
				|| (vertrag.verlaengerung() == Verlaengerung.ZAHLUNGSINTERVALL && vertrag.zahlweise() == Zahlweise.MONATLICH);
	}

	/**
	 * Letzter Tag für den Zugang der Kündigung: Ende minus Frist. Liegt das Ende auf einem Monatsende,
	 * auch „kündigen bis“ (30.11. mit einem Monat → 31.10., nicht 30.10.).
	 */
	static LocalDate kuendigenBis(LocalDate ende, int fristMonate) {
		return plusMonate(ende, -fristMonate);
	}

	/** Verschiebt um Monate und hält dabei ein Monatsende (28.02. + 12 Monate im Schaltjahr → 29.02.). */
	static LocalDate plusMonate(LocalDate datum, long monate) {
		LocalDate verschoben = datum.plusMonths(monate);
		return datum.getDayOfMonth() == datum.lengthOfMonth()
				? verschoben.withDayOfMonth(verschoben.lengthOfMonth())
				: verschoben;
	}

	/** Schätzt den ersten Schritt, damit weit zurückliegende Daten nicht Monat für Monat gezählt werden. */
	private long ersterSchritt(LocalDate anker, int schrittMonate) {
		return Math.max(0, ChronoUnit.MONTHS.between(anker, heute) / schrittMonate - 1);
	}

}
