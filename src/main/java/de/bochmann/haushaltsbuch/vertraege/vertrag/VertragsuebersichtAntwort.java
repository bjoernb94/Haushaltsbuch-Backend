package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.uebersicht.Vertragsuebersicht;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Kennzahlen der Verträge für die Startseite und die Seite „Verträge“, zum heutigen Tag berechnet; gerechnet in
 * {@link Vertragsuebersicht}. Bei geteilten Verträgen zählen die Kosten nur mit dem eigenen Anteil.
 *
 * @param laufendeVertraege Verträge, die begonnen haben und nicht beendet oder storniert sind (gekündigte zählen bis
 *        zum Ende mit, noch nicht begonnene ab ihrem Beginn)
 * @param kostenProMonat Summe über die laufenden Verträge, je Währung ein Betrag, nach Währungscode sortiert;
 *        leer ohne laufende Verträge
 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr {@code jahr} je Währung, über alle Verträge mit
 *        Abbuchungen im Jahr: laufende, im Jahr beendete und noch nicht begonnene; leer ohne solche Verträge
 * @param kostenImJahrMitPrognose ob ein Vertrag mit Prognose in {@code kostenImJahr} eingeht („inkl. Prognose“)
 * @param einmalkostenImJahr Summe der im Kalenderjahr {@code jahr} fälligen Einmalkosten je Währung, über alle
 *        Verträge, auch beendete; leer ohne solche Einträge
 * @param jahr das laufende Jahr laut Backend
 * @param hatAnteile ob ein Vertrag in {@code kostenProMonat}, {@code kostenImJahr} oder {@code einmalkostenImJahr}
 *        nur mit einem Anteil unter 100&nbsp;% zählt, für den Hinweis „Bei geteilten Verträgen nur dein Anteil“
 * @param hatNichtBegonnene ob ein Vertrag wegen eines späteren Beginns nicht in {@code kostenProMonat} zählt, für den
 *        Hinweis „Ohne Verträge, die erst später beginnen“
 * @param naechsteKuendigungsfristen die {@value Vertragsuebersicht#FRISTEN} nächsten mit {@link Kuendigungsart#KUENDIGEN_BIS}, auch noch
 *        nicht begonnene, in der Reihenfolge der Vertragsliste
 */
record VertragsuebersichtAntwort(
		int laufendeVertraege,
		List<Betrag> kostenProMonat,
		List<Betrag> kostenImJahr,
		boolean kostenImJahrMitPrognose,
		List<Betrag> einmalkostenImJahr,
		int jahr,
		boolean hatAnteile,
		boolean hatNichtBegonnene,
		List<AnstehendeKuendigung> naechsteKuendigungsfristen) {

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

	/** Die Kennzahlen aus der {@link Vertragsuebersicht}, deren Fristen als {@link AnstehendeKuendigung}. */
	static VertragsuebersichtAntwort aus(Vertragsuebersicht<VertragAntwort> uebersicht, int jahr) {
		return new VertragsuebersichtAntwort(uebersicht.laufendeVertraege(), uebersicht.kostenProMonat(),
				uebersicht.kostenImJahr(), uebersicht.kostenImJahrMitPrognose(), uebersicht.einmalkostenImJahr(), jahr,
				uebersicht.hatAnteile(), uebersicht.hatNichtBegonnene(),
				uebersicht.naechsteKuendigungsfristen().stream().map(AnstehendeKuendigung::aus).toList());
	}

}
