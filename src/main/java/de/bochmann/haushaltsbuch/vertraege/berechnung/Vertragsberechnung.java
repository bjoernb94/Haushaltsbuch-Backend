package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Berechnete Angaben eines Vertrags zu einem Stichtag ({@code heute}): Status, Abbuchungen, Kosten im Jahr,
 * Preisänderung und Kündigung. Ohne Spring und ohne Uhr, damit Tests jeden Tag festlegen können; die Aufrufer geben
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

	/** Der Stichtag; sein Jahr ist das laufende Jahr für {@link #kostenImJahr}. */
	public LocalDate heute() {
		return heute;
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
		if (endetMitLaufzeitende(vertrag) && vertrag.laufzeitende().isBefore(heute)) {
			return Vertragsstatus.BEENDET;
		}
		return Vertragsstatus.AKTIV;
	}

	/**
	 * Die erste Abbuchung ab heute (und nicht vor {@code abbuchungAb}) im Takt der Zahlweise; leer, wenn
	 * der Vertrag beendet ist, vorher endet (Kündigungsdatum oder Laufzeitende ohne Verlängerung, das
	 * frühere von beiden; eine Abbuchung genau an diesem Tag zählt noch) oder Angaben fehlen.
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
		Optional<LocalDate> ende = ende(vertrag);
		if (ende.isPresent() && abbuchung.isAfter(ende.get())) {
			return Optional.empty();
		}
		return Optional.of(abbuchung);
	}

	/** Wie {@link #naechsteAbbuchung(Vertragsdaten)}, mit dem an diesem Tag gültigen Preis. */
	public Optional<Abbuchung> naechsteAbbuchung(Vertragsdaten vertrag, Preisliste preise) {
		return naechsteAbbuchung(vertrag).map(am -> new Abbuchung(am, preise.am(am)));
	}

	/**
	 * Alle Abbuchungen im laufenden Kalenderjahr, auch die schon erfolgten: von {@code abbuchungAb} aus im Takt der
	 * Zahlweise vorwärts und rückwärts ({@code abbuchungAb} ist nur der Anker), nicht vor {@code beginn} und nicht
	 * nach dem Vertragsende (eine Abbuchung genau an diesem Tag zählt noch). Leer, wenn Angaben fehlen.
	 */
	public List<LocalDate> abbuchungenImJahr(Vertragsdaten vertrag) {
		if (vertrag.zahlweise() == null || vertrag.abbuchungAb() == null) {
			return List.of();
		}
		LocalDate von = LocalDate.of(heute.getYear(), 1, 1);
		if (vertrag.beginn() != null && vertrag.beginn().isAfter(von)) {
			von = vertrag.beginn();
		}
		LocalDate bis = LocalDate.of(heute.getYear(), 12, 31);
		Optional<LocalDate> ende = ende(vertrag);
		if (ende.isPresent() && ende.get().isBefore(bis)) {
			bis = ende.get();
		}
		int schritt = vertrag.zahlweise().monate();
		LocalDate anker = vertrag.abbuchungAb();
		// Geschätzt und dann vorgezählt; negativ, wenn der Anker nach dem Jahresbeginn liegt.
		long k = ChronoUnit.MONTHS.between(anker, von) / schritt - 1;
		while (anker.plusMonths(k * schritt).isBefore(von)) {
			k++;
		}
		List<LocalDate> abbuchungen = new ArrayList<>();
		for (LocalDate abbuchung = anker.plusMonths(k * schritt); !abbuchung.isAfter(bis);
				abbuchung = anker.plusMonths(++k * schritt)) {
			abbuchungen.add(abbuchung);
		}
		return abbuchungen;
	}

	/** Summe der {@link #abbuchungenImJahr Abbuchungen im laufenden Jahr}, jede mit ihrem Preis. */
	public Betrag kostenImJahr(Vertragsdaten vertrag, Preisliste preise) {
		return preise.summe(abbuchungenImJahr(vertrag));
	}

	/** Siehe {@link Preisliste#naechsteAenderung}; leer bei einem beendeten Vertrag. */
	public Optional<Preisaenderung> naechstePreisaenderung(Vertragsdaten vertrag, Preisliste preise) {
		if (status(vertrag) == Vertragsstatus.BEENDET) {
			return Optional.empty();
		}
		return preise.naechsteAenderung(heute, ende(vertrag));
	}

	/**
	 * Letzter Tag des Vertrags: {@code gekuendigtZum} oder das Laufzeitende, wenn er damit endet (kündbar, ohne
	 * Verlängerung), bei beiden das frühere; leer, wenn er nicht endet.
	 */
	private static Optional<LocalDate> ende(Vertragsdaten vertrag) {
		LocalDate ende = vertrag.gekuendigtZum();
		if (endetMitLaufzeitende(vertrag) && (ende == null || vertrag.laufzeitende().isBefore(ende))) {
			ende = vertrag.laufzeitende();
		}
		return Optional.ofNullable(ende);
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

	/** Kündbar, ohne Verlängerung und mit Laufzeitende: Der Vertrag endet dann von selbst. */
	private static boolean endetMitLaufzeitende(Vertragsdaten vertrag) {
		return vertrag.kuendbar() && vertrag.verlaengerung() == Verlaengerung.KEINE && vertrag.laufzeitende() != null;
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
