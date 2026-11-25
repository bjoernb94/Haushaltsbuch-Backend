package de.bochmann.haushaltsbuch.vertraege.berechnung.uebersicht;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragswerte;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Einmalkosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Kosten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsstatus;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kennzahlen über alle Verträge eines Haushalts zum Stichtag der Berechnung, für die Startseite und die Seite
 * „Verträge“. Bei geteilten Verträgen zählen die Kosten nur mit dem eigenen Anteil; summiert wird ungerundet und je
 * Währung, gerundet erst die Summe (ADR 0011).
 *
 * @param <T> der Vertrag, wie ihn der Aufrufer für {@code naechsteKuendigungsfristen} braucht
 * @param laufendeVertraege Verträge, die begonnen haben und nicht beendet oder storniert sind (gekündigte zählen bis
 *        zum Ende mit, noch nicht begonnene ab ihrem Beginn)
 * @param kostenProMonat Summe über die laufenden Verträge, je Währung ein Betrag, nach Währungscode sortiert;
 *        leer ohne laufende Verträge
 * @param kostenImJahr Summe der Abbuchungen im Kalenderjahr je Währung, über alle Verträge mit Abbuchungen im Jahr:
 *        laufende, im Jahr beendete und noch nicht begonnene; leer ohne solche Verträge
 * @param kostenImJahrMitPrognose ob ein Vertrag mit Prognose in {@code kostenImJahr} eingeht
 * @param einmalkostenImJahr Summe der im Kalenderjahr fälligen Einmalkosten je Währung, über alle Verträge, auch
 *        beendete; leer ohne solche Einträge
 * @param hatAnteile ob ein Vertrag in {@code kostenProMonat}, {@code kostenImJahr} oder {@code einmalkostenImJahr}
 *        nur mit einem Anteil unter 100&nbsp;% zählt
 * @param hatNichtBegonnene ob ein Vertrag wegen eines späteren Beginns nicht in {@code kostenProMonat} zählt
 * @param naechsteKuendigungsfristen die {@value #FRISTEN} ersten mit {@link Kuendigungsart#KUENDIGEN_BIS}, auch noch
 *        nicht begonnene, in der Reihenfolge der übergebenen Verträge
 */
public record Vertragsuebersicht<T>(
		int laufendeVertraege,
		List<Betrag> kostenProMonat,
		List<Betrag> kostenImJahr,
		boolean kostenImJahrMitPrognose,
		List<Betrag> einmalkostenImJahr,
		boolean hatAnteile,
		boolean hatNichtBegonnene,
		List<T> naechsteKuendigungsfristen) {

	public static final int FRISTEN = 5;

	/**
	 * Ein gespeicherter Vertrag mit seiner Grundlage und den daraus berechneten Werten; gespeichert ist alles
	 * vollständig, Betrag und Kosten im Jahr sind also gesetzt.
	 */
	public record Eintrag<T>(T vertrag, Berechnungsgrundlage grundlage, Vertragswerte werte) {
	}

	/**
	 * @param vertraege alle Verträge des Haushalts in der Reihenfolge der Liste
	 * @param jahr das laufende Jahr, für das {@code kostenImJahr} der Werte gerechnet ist
	 */
	public static <T> Vertragsuebersicht<T> aus(List<Eintrag<T>> vertraege, int jahr) {
		var laufende = vertraege.stream()
				.filter(Vertragsuebersicht::laeuft)
				.toList();
		// Beendete und noch nicht begonnene zählen mit, sobald sie im Jahr Abbuchungen haben (Preise sind größer 0),
		// stornierte haben keine; enthält die laufenden.
		var imJahrVertraege = vertraege.stream()
				.filter(vertrag -> laeuft(vertrag) || vertrag.werte().kostenImJahr().wert().signum() != 0)
				.toList();
		// Je Vertrag der ungerundete Anteil am exakten Jahreswert; gerundet wird erst die Summe (ADR 0011), nicht
		// eigenerAnteil der Werte.
		List<Betrag> proJahr = jeWaehrung(laufende.stream()
				.map(vertrag -> vertrag.grundlage().anteil()
						.von(Kosten.proJahr(vertrag.werte().betrag(), vertrag.grundlage().daten().zahlweise()))));
		List<Betrag> imJahr = jeWaehrung(imJahrVertraege.stream()
				.map(vertrag -> vertrag.grundlage().anteil().von(vertrag.werte().kostenImJahr())));
		// Alle Verträge, auch beendete, mit Einmalkosten im Jahr (Beträge sind größer 0).
		var mitEinmalkosten = vertraege.stream()
				.filter(vertrag -> einmalkostenImJahr(vertrag, jahr).wert().signum() != 0)
				.toList();
		List<Betrag> einmalImJahr = jeWaehrung(mitEinmalkosten.stream()
				.map(vertrag -> vertrag.grundlage().anteil().von(einmalkostenImJahr(vertrag, jahr))));
		boolean hatAnteile = Stream.concat(imJahrVertraege.stream(), mitEinmalkosten.stream())
				.anyMatch(vertrag -> vertrag.grundlage().anteil().geteilt());
		boolean hatNichtBegonnene = vertraege.stream()
				.anyMatch(vertrag -> vertrag.werte().status() == Vertragsstatus.NICHT_BEGONNEN);
		// Beendete und stornierte haben nie KUENDIGEN_BIS.
		var fristen = vertraege.stream()
				.filter(vertrag -> vertrag.werte().kuendigung().art() == Kuendigungsart.KUENDIGEN_BIS)
				.limit(FRISTEN)
				.map(Eintrag::vertrag)
				.toList();
		return new Vertragsuebersicht<>(laufende.size(), proJahr.stream().map(Kosten::proMonat).toList(),
				imJahr.stream().map(Betrag::gerundet).toList(),
				imJahrVertraege.stream().anyMatch(vertrag -> vertrag.werte().kostenImJahrMitPrognose()),
				einmalImJahr.stream().map(Betrag::gerundet).toList(), hatAnteile, hatNichtBegonnene, fristen);
	}

	/** Begonnen und weder beendet noch storniert (Stand heute). */
	private static boolean laeuft(Eintrag<?> vertrag) {
		var status = vertrag.werte().status();
		return status == Vertragsstatus.AKTIV || status == Vertragsstatus.GEKUENDIGT;
	}

	/** Die im Jahr fälligen Einmalkosten des Vertrags, voll. */
	private static Betrag einmalkostenImJahr(Eintrag<?> vertrag, int jahr) {
		return Einmalkosten.imJahr(vertrag.grundlage().einmalkosten(), jahr, vertrag.werte().betrag().waehrung());
	}

	/** Summe je Währung, nach Währungscode sortiert. */
	private static List<Betrag> jeWaehrung(Stream<Betrag> betraege) {
		return List.copyOf(betraege
				.collect(Collectors.toMap(betrag -> betrag.waehrung().getCurrencyCode(), betrag -> betrag, Betrag::plus,
						TreeMap::new))
				.values());
	}

}
