package de.bochmann.haushaltsbuch.vertraege.berechnung.kosten;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Laufzeitberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abbuchungen und Kosten im laufenden Kalenderjahr zum Stichtag der {@link Laufzeitberechnung}; Teil der
 * {@link de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung Vertragsberechnung}. Die Umrechnung auf
 * Jahr und Monat ohne Kalender macht {@link Kosten}.
 */
public class Kostenberechnung {

	private final Laufzeitberechnung laufzeit;

	private final Preisberechnung preise;

	private final LocalDate heute;

	public Kostenberechnung(Laufzeitberechnung laufzeit, Preisberechnung preise) {
		this.laufzeit = laufzeit;
		this.preise = preise;
		this.heute = laufzeit.heute();
	}

	/**
	 * Alle Abbuchungen im laufenden Kalenderjahr, auch die schon erfolgten und die eines noch nicht begonnenen
	 * Vertrags: je Phase ab ihrem Beginn im Takt der Zahlweise, nicht nach dem Vertragsende (eine Abbuchung genau an
	 * diesem Tag zählt noch; ein stornierter Vertrag hat keine). In einer {@link Unterbrechung} wird nichts abgebucht,
	 * danach beginnt der Takt neu. Leer, wenn Angaben fehlen.
	 */
	public List<LocalDate> abbuchungenImJahr(Vertragsdaten vertrag) {
		if (vertrag.zahlweise() == null || vertrag.beginn() == null) {
			return List.of();
		}
		LocalDate jahresanfang = LocalDate.of(heute.getYear(), 1, 1);
		LocalDate jahresende = jahresende();
		Optional<LocalDate> ende = laufzeit.vertragsende(vertrag);
		List<LocalDate> abbuchungen = new ArrayList<>();
		LocalDate phasenbeginn = vertrag.beginn();
		for (Unterbrechung unterbrechung : vertrag.unterbrechungen()) {
			abbuchungen.addAll(abbuchungen(phasenbeginn, unterbrechung.von().minusDays(1), vertrag.zahlweise(),
					jahresanfang, jahresende));
			phasenbeginn = unterbrechung.wiederAb();
		}
		LocalDate letzterTag = ende.isPresent() && ende.get().isBefore(jahresende) ? ende.get() : jahresende;
		abbuchungen.addAll(abbuchungen(phasenbeginn, letzterTag, vertrag.zahlweise(), jahresanfang, jahresende));
		return abbuchungen;
	}

	/**
	 * Die Abbuchungen einer Phase ab {@code anker} im Takt der Zahlweise, nur die von {@code jahresanfang} bis
	 * {@code jahresende} und bis einschließlich {@code phasenende}.
	 */
	private static List<LocalDate> abbuchungen(LocalDate anker, LocalDate phasenende, Zahlweise zahlweise,
			LocalDate jahresanfang, LocalDate jahresende) {
		LocalDate bis = phasenende.isBefore(jahresende) ? phasenende : jahresende;
		int schritt = zahlweise.monate();
		// Geschätzt und dann vorgezählt; nie vor dem Anker.
		long k = Math.max(0, ChronoUnit.MONTHS.between(anker, jahresanfang) / schritt - 1);
		while (anker.plusMonths(k * schritt).isBefore(jahresanfang)) {
			k++;
		}
		List<LocalDate> abbuchungen = new ArrayList<>();
		for (LocalDate abbuchung = anker.plusMonths(k * schritt); !abbuchung.isAfter(bis);
				abbuchung = anker.plusMonths(++k * schritt)) {
			abbuchungen.add(abbuchung);
		}
		return abbuchungen;
	}

	/**
	 * Summe der {@link #abbuchungenImJahr Abbuchungen im laufenden Jahr}, jede mit ihrem Preis, auch einem
	 * errechneten; ab einem Termin der Prognose mit dem prognostizierten (siehe {@link Preisberechnung#mitPrognose}).
	 */
	public Betrag kostenImJahr(Vertragsdaten vertrag, Preisliste liste, Preisprognose prognose) {
		return preise.mitPrognose(vertrag, liste, prognose, jahresende()).summe(abbuchungenImJahr(vertrag));
	}

	/**
	 * Ob eine Abbuchung in {@link #kostenImJahr} mit einem nicht gespeicherten Preis rechnet: einem prognostizierten
	 * oder einem errechneten.
	 */
	public boolean kostenImJahrMitPrognose(Vertragsdaten vertrag, Preisliste liste, Preisprognose prognose) {
		var ergaenzt = preise.mitPrognose(vertrag, liste, prognose, jahresende());
		return abbuchungenImJahr(vertrag).stream().map(ergaenzt::eintragAm).map(Preis::herkunft)
				.anyMatch(herkunft -> herkunft == Preisherkunft.ERRECHNET || herkunft == Preisherkunft.PROGNOSE);
	}

	private LocalDate jahresende() {
		return LocalDate.of(heute.getYear(), 12, 31);
	}

}
