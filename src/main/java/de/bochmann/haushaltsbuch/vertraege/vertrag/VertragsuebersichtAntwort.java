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

/**
 * Kennzahlen der Verträge für die Startseite und die Seite „Verträge“, zum heutigen Tag berechnet.
 *
 * @param laufendeVertraege Verträge, die nicht beendet sind (gekündigte zählen bis zum Ende mit)
 * @param kostenProMonat Summe über die laufenden Verträge, je Währung ein Betrag, nach Währungscode sortiert;
 *        leer ohne laufende Verträge
 * @param kostenProJahr wie {@code kostenProMonat}
 * @param naechsteKuendigungsfristen die {@value #FRISTEN} nächsten mit {@link Kuendigungsart#KUENDIGEN_BIS},
 *        in der Reihenfolge der Vertragsliste
 */
record VertragsuebersichtAntwort(
		int laufendeVertraege,
		List<Betrag> kostenProMonat,
		List<Betrag> kostenProJahr,
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
	 */
	static VertragsuebersichtAntwort aus(List<VertragAntwort> vertraege) {
		var laufende = vertraege.stream()
				.filter(vertrag -> vertrag.berechnet().status() != Vertragsstatus.BEENDET)
				.toList();
		// Die Jahreswerte sind exakt; gerundet wird erst beim Monatswert der Summe (siehe Kosten).
		List<Betrag> proJahr = List.copyOf(laufende.stream()
				.map(vertrag -> vertrag.berechnet().kostenProJahr())
				.collect(Collectors.toMap(betrag -> betrag.waehrung().getCurrencyCode(), betrag -> betrag, Betrag::plus,
						TreeMap::new))
				.values());
		var fristen = laufende.stream()
				.filter(vertrag -> vertrag.berechnet().kuendigung().art() == Kuendigungsart.KUENDIGEN_BIS)
				.limit(FRISTEN)
				.map(AnstehendeKuendigung::aus)
				.toList();
		return new VertragsuebersichtAntwort(laufende.size(), proJahr.stream().map(Kosten::proMonat).toList(), proJahr,
				fristen);
	}

}
