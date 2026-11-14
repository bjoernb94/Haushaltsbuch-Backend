package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kennzahlen der Verträge für die Startseite und die Seite „Verträge“, zum heutigen Tag berechnet.
 *
 * @param laufendeVertraege Verträge, die nicht beendet sind (gekündigte zählen bis zum Ende mit)
 * @param kostenProMonat Summe über die laufenden Verträge, je Währung ein Betrag, nach Währungscode sortiert;
 *        leer ohne laufende Verträge
 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr {@code jahr} je Währung, über die laufenden Verträge und
 *        die im Jahr beendeten (die bis dahin gekostet haben); leer ohne solche Verträge
 * @param jahr das laufende Jahr laut Backend
 * @param naechsteKuendigungsfristen die {@value #FRISTEN} nächsten mit {@link Kuendigungsart#KUENDIGEN_BIS},
 *        in der Reihenfolge der Vertragsliste
 */
record VertragsuebersichtAntwort(
		int laufendeVertraege,
		List<Betrag> kostenProMonat,
		List<Betrag> kostenImJahr,
		int jahr,
		List<AnstehendeKuendigung> naechsteKuendigungsfristen) {

	static final int FRISTEN = 5;

	/**
	 * Nur was die Kachel braucht, nicht der ganze Vertrag (Datensparsamkeit); {@code kuendigenBis}, {@code zum}
	 * und {@code dringend} wie in {@code berechnet.kuendigung}.
	 */
	record AnstehendeKuendigung(
			UUID id,
			String anbieter,
			@Nullable String bezeichnung,
			Betrag betrag,
			Zahlweise zahlweise,
			LocalDate kuendigenBis,
			LocalDate zum,
			boolean dringend) {

		static AnstehendeKuendigung aus(VertragAntwort vertrag) {
			var kuendigung = vertrag.berechnet().kuendigung();
			return new AnstehendeKuendigung(vertrag.id(), vertrag.anbieter(), vertrag.bezeichnung(), vertrag.betrag(),
					vertrag.zahlweise(), kuendigung.kuendigenBis(), kuendigung.zum(), kuendigung.dringend());
		}

	}

	/**
	 * @param vertraege alle Verträge des Haushalts in der Reihenfolge der Liste
	 * @param jahr das laufende Jahr, für das {@code berechnet.kostenImJahr} gerechnet ist
	 */
	static VertragsuebersichtAntwort aus(List<VertragAntwort> vertraege, int jahr) {
		var laufende = vertraege.stream()
				.filter(vertrag -> vertrag.berechnet().status() != Vertragsstatus.BEENDET)
				.toList();
		// Die Jahreswerte der aktuellen Beträge sind exakt; gerundet wird erst beim Monatswert der Summe (ADR 0011).
		List<Betrag> proJahr = jeWaehrung(laufende.stream()
				.map(vertrag -> Kosten.proJahr(vertrag.betrag(), vertrag.zahlweise())));
		// Beendete zählen mit, solange sie im Jahr etwas gekostet haben (Preise sind größer 0).
		List<Betrag> imJahr = jeWaehrung(vertraege.stream()
				.filter(vertrag -> vertrag.berechnet().status() != Vertragsstatus.BEENDET
						|| vertrag.berechnet().kostenImJahr().wert().signum() != 0)
				.map(vertrag -> vertrag.berechnet().kostenImJahr()));
		var fristen = laufende.stream()
				.filter(vertrag -> vertrag.berechnet().kuendigung().art() == Kuendigungsart.KUENDIGEN_BIS)
				.limit(FRISTEN)
				.map(AnstehendeKuendigung::aus)
				.toList();
		return new VertragsuebersichtAntwort(laufende.size(), proJahr.stream().map(Kosten::proMonat).toList(), imJahr,
				jahr, fristen);
	}

	/** Summe je Währung, nach Währungscode sortiert. */
	private static List<Betrag> jeWaehrung(Stream<Betrag> betraege) {
		return List.copyOf(betraege
				.collect(Collectors.toMap(betrag -> betrag.waehrung().getCurrencyCode(), betrag -> betrag, Betrag::plus,
						TreeMap::new))
				.values());
	}

}
