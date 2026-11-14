package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Einmalkosten;
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
 * Bei geteilten Verträgen zählen die Kosten nur mit dem eigenen Anteil.
 *
 * @param laufendeVertraege Verträge, die nicht beendet sind (gekündigte zählen bis zum Ende mit)
 * @param kostenProMonat Summe über die laufenden Verträge, je Währung ein Betrag, nach Währungscode sortiert;
 *        leer ohne laufende Verträge
 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr {@code jahr} je Währung, über die laufenden Verträge und
 *        die im Jahr beendeten (die bis dahin gekostet haben); leer ohne solche Verträge
 * @param einmalkostenImJahr Summe der im Kalenderjahr {@code jahr} fälligen Einmalkosten je Währung, über alle
 *        Verträge, auch beendete; leer ohne solche Einträge
 * @param jahr das laufende Jahr laut Backend
 * @param hatAnteile ob ein Vertrag in {@code kostenProMonat}, {@code kostenImJahr} oder {@code einmalkostenImJahr}
 *        nur mit einem Anteil unter 100&nbsp;% zählt, für den Hinweis „Bei geteilten Verträgen nur dein Anteil“
 * @param naechsteKuendigungsfristen die {@value #FRISTEN} nächsten mit {@link Kuendigungsart#KUENDIGEN_BIS},
 *        in der Reihenfolge der Vertragsliste
 */
record VertragsuebersichtAntwort(
		int laufendeVertraege,
		List<Betrag> kostenProMonat,
		List<Betrag> kostenImJahr,
		List<Betrag> einmalkostenImJahr,
		int jahr,
		boolean hatAnteile,
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
		// Beendete zählen mit, solange sie im Jahr etwas gekostet haben (Preise sind größer 0); enthält die laufenden.
		var imJahrVertraege = vertraege.stream()
				.filter(vertrag -> vertrag.berechnet().status() != Vertragsstatus.BEENDET
						|| vertrag.berechnet().kostenImJahr().wert().signum() != 0)
				.toList();
		// Je Vertrag der ungerundete Anteil am exakten Jahreswert; gerundet wird erst die Summe (ADR 0011), nicht
		// berechnet.eigenerAnteil.
		List<Betrag> proJahr = jeWaehrung(laufende.stream()
				.map(vertrag -> anteil(vertrag).von(Kosten.proJahr(vertrag.betrag(), vertrag.zahlweise()))));
		List<Betrag> imJahr = jeWaehrung(imJahrVertraege.stream()
				.map(vertrag -> anteil(vertrag).von(vertrag.berechnet().kostenImJahr())));
		// Alle Verträge, auch beendete, mit Einmalkosten im Jahr (Beträge sind größer 0).
		var mitEinmalkosten = vertraege.stream()
				.filter(vertrag -> einmalkostenImJahr(vertrag, jahr).wert().signum() != 0)
				.toList();
		List<Betrag> einmalImJahr = jeWaehrung(mitEinmalkosten.stream()
				.map(vertrag -> anteil(vertrag).von(einmalkostenImJahr(vertrag, jahr))));
		boolean hatAnteile = Stream.concat(imJahrVertraege.stream(), mitEinmalkosten.stream())
				.anyMatch(vertrag -> anteil(vertrag).geteilt());
		var fristen = laufende.stream()
				.filter(vertrag -> vertrag.berechnet().kuendigung().art() == Kuendigungsart.KUENDIGEN_BIS)
				.limit(FRISTEN)
				.map(AnstehendeKuendigung::aus)
				.toList();
		return new VertragsuebersichtAntwort(laufende.size(), proJahr.stream().map(Kosten::proMonat).toList(),
				imJahr.stream().map(Betrag::gerundet).toList(), einmalImJahr.stream().map(Betrag::gerundet).toList(), jahr,
				hatAnteile, fristen);
	}

	/** Die im Jahr fälligen Einmalkosten des Vertrags, voll. */
	private static Betrag einmalkostenImJahr(VertragAntwort vertrag, int jahr) {
		return Einmalkosten.imJahr(vertrag.einmalkosten().stream()
				.map(eintrag -> new Einmalkosten(eintrag.faelligAm(), eintrag.betrag()))
				.toList(), jahr, vertrag.betrag().waehrung());
	}

	private static Anteil anteil(VertragAntwort vertrag) {
		return Anteil.aus(vertrag.anteilProzent());
	}

	/** Summe je Währung, nach Währungscode sortiert. */
	private static List<Betrag> jeWaehrung(Stream<Betrag> betraege) {
		return List.copyOf(betraege
				.collect(Collectors.toMap(betrag -> betrag.waehrung().getCurrencyCode(), betrag -> betrag, Betrag::plus,
						TreeMap::new))
				.values());
	}

}
