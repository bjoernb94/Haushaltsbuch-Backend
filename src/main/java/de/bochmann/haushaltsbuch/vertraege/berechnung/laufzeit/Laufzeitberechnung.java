package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Status, Vertragsende, Wiederaufnahme und Kündigung eines Vertrags zu einem Stichtag ({@code heute}). Ohne Spring
 * und ohne Uhr, damit Tests jeden Tag festlegen können; Teil der
 * {@link de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung Vertragsberechnung}.
 * <p>
 * Weitergezählt wird immer vom gespeicherten Datum aus ({@code beginn}, Laufzeitende), siehe {@link Monate}.
 */
public class Laufzeitberechnung {

	/** Ab so vielen Tagen bis „kündigen bis“ gilt eine Kündigung als dringend. */
	public static final int DRINGEND_TAGE = 45;

	private final LocalDate heute;

	public Laufzeitberechnung(LocalDate heute) {
		this.heute = heute;
	}

	/** Der Stichtag. */
	public LocalDate heute() {
		return heute;
	}

	/**
	 * {@link Vertragsstatus#STORNIERT}, wenn das Kündigungsdatum vor dem Beginn liegt (der Vertrag läuft nie);
	 * {@link Vertragsstatus#NICHT_BEGONNEN}, solange der Beginn noch kommt, auch wenn schon gekündigt;
	 * {@link Vertragsstatus#UNTERBROCHEN}, wenn heute in einer {@link Unterbrechung} liegt;
	 * {@link Vertragsstatus#BEENDET}, wenn das Kündigungsdatum vorbei ist oder ein Vertrag ohne Verlängerung sein
	 * Laufzeitende überschritten hat; {@link Vertragsstatus#GEKUENDIGT}, solange das Kündigungsdatum noch kommt;
	 * sonst {@link Vertragsstatus#AKTIV}.
	 */
	public Vertragsstatus status(Vertragsdaten vertrag) {
		if (vertrag.beginn() != null && vertrag.gekuendigtZum() != null
				&& vertrag.gekuendigtZum().isBefore(vertrag.beginn())) {
			return Vertragsstatus.STORNIERT;
		}
		if (vertrag.beginn() != null && vertrag.beginn().isAfter(heute)) {
			return Vertragsstatus.NICHT_BEGONNEN;
		}
		if (unterbrechungHeute(vertrag).isPresent()) {
			return Vertragsstatus.UNTERBROCHEN;
		}
		if (vertrag.gekuendigtZum() != null) {
			return vertrag.gekuendigtZum().isBefore(heute) ? Vertragsstatus.BEENDET : Vertragsstatus.GEKUENDIGT;
		}
		if (endetMitLaufzeitende(vertrag) && vertrag.laufzeitende().isBefore(heute)) {
			return Vertragsstatus.BEENDET;
		}
		return Vertragsstatus.AKTIV;
	}

	/**
	 * Beendet, storniert oder unterbrochen: Bis auf Weiteres kommt nichts mehr, auch keine Preisänderung (bei einer
	 * Unterbrechung zeigt die SPA stattdessen „Wieder ab“).
	 */
	public boolean vorbei(Vertragsdaten vertrag) {
		var status = status(vertrag);
		return status == Vertragsstatus.BEENDET || status == Vertragsstatus.STORNIERT
				|| status == Vertragsstatus.UNTERBROCHEN;
	}

	/**
	 * Letzter Tag des Vertrags: {@code gekuendigtZum} oder das Laufzeitende, wenn er damit endet (kündbar, ohne
	 * Verlängerung), bei beiden das frühere; leer, wenn er nicht endet.
	 */
	public Optional<LocalDate> vertragsende(Vertragsdaten vertrag) {
		LocalDate ende = vertrag.gekuendigtZum();
		if (endetMitLaufzeitende(vertrag) && (ende == null || vertrag.laufzeitende().isBefore(ende))) {
			ende = vertrag.laufzeitende();
		}
		return Optional.ofNullable(ende);
	}

	/**
	 * Wann sich der Vertrag wieder aufnehmen lässt: nur gekündigt oder beendet und mit Vertragsende, frühestens am Tag
	 * danach; leer sonst (z.&nbsp;B. storniert, aktiv oder unterbrochen).
	 */
	public Optional<Wiederaufnahmetermin> wiederaufnahme(Vertragsdaten vertrag) {
		var status = status(vertrag);
		if (status != Vertragsstatus.GEKUENDIGT && status != Vertragsstatus.BEENDET) {
			return Optional.empty();
		}
		return vertragsende(vertrag).map(ende -> {
			LocalDate fruehestens = ende.plusDays(1);
			return new Wiederaufnahmetermin(fruehestens, fruehestens.isAfter(heute) ? fruehestens : heute);
		});
	}

	/** Die {@link Unterbrechung}, in der heute liegt; leer, wenn der Vertrag heute nicht unterbrochen ist. */
	private Optional<Unterbrechung> unterbrechungHeute(Vertragsdaten vertrag) {
		return vertrag.unterbrechungen().stream().filter(unterbrechung -> unterbrechung.enthaelt(heute)).findFirst();
	}

	/** Art und Termine der Kündigung; die Regeln stehen in {@code docs/api.md}, Abschnitt Verträge. */
	public Kuendigung kuendigung(Vertragsdaten vertrag) {
		Integer frist = vertrag.kuendigungsfristMonate();
		switch (status(vertrag)) {
			case BEENDET -> {
				return Kuendigung.ohneDaten(Kuendigungsart.BEENDET, frist);
			}
			case STORNIERT -> {
				return Kuendigung.ohneDaten(Kuendigungsart.STORNIERT, frist);
			}
			case UNTERBROCHEN -> {
				return new Kuendigung(Kuendigungsart.WIEDER_AB, null, unterbrechungHeute(vertrag).orElseThrow().wiederAb(),
						frist, false);
			}
			case GEKUENDIGT -> {
				return new Kuendigung(Kuendigungsart.GEKUENDIGT, null, vertrag.gekuendigtZum(), frist, false);
			}
			case NICHT_BEGONNEN -> {
				// Schon gekündigt (zum Beginn oder später), sonst wie ein laufender Vertrag.
				if (vertrag.gekuendigtZum() != null) {
					return new Kuendigung(Kuendigungsart.GEKUENDIGT, null, vertrag.gekuendigtZum(), frist, false);
				}
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
			while (kuendigenBis(verlaengertesEnde(vertrag, k, schritt), fristMonate).isBefore(heute)) {
				k++;
			}
			ende = verlaengertesEnde(vertrag, k, schritt);
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
	 * Das erste Vertragsende, ab dem verlängert wird: das Laufzeitende; ohne Laufzeitende eine Verlängerung nach dem
	 * {@link Vertragsdaten#aktuellerBeginn aktuellen Beginn} (Zahlungsintervall, ein Monat oder zwölf Monate, siehe
	 * {@link Monate#endeNach}). Ohne Verlängerung zählt nur das Laufzeitende selbst.
	 */
	private static LocalDate erstesEnde(Vertragsdaten vertrag) {
		if (vertrag.laufzeitende() != null) {
			return vertrag.laufzeitende();
		}
		int schritt = verlaengerungMonate(vertrag);
		return vertrag.beginn() != null && schritt > 0 ? Monate.endeNach(vertrag.aktuellerBeginn(), schritt) : null;
	}

	/**
	 * Das Vertragsende nach {@code k} weiteren Verlängerungen um {@code schritt} Monate: vom Laufzeitende aus (ein
	 * Monatsende bleibt eins, siehe {@link Monate#plus}), ohne Laufzeitende vom aktuellen Beginn aus, nicht vom vorigen
	 * Ende.
	 */
	private static LocalDate verlaengertesEnde(Vertragsdaten vertrag, long k, int schritt) {
		return vertrag.laufzeitende() != null ? Monate.plus(vertrag.laufzeitende(), k * schritt)
				: Monate.endeNach(vertrag.aktuellerBeginn(), (k + 1) * schritt);
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
		return Monate.plus(ende, -fristMonate);
	}

	/** Schätzt den ersten Schritt, damit weit zurückliegende Daten nicht Monat für Monat gezählt werden. */
	private long ersterSchritt(LocalDate anker, int schrittMonate) {
		return Math.max(0, ChronoUnit.MONTHS.between(anker, heute) / schrittMonate - 1);
	}

}
