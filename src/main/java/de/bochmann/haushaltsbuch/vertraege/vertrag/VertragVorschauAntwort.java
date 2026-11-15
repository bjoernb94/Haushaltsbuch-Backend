package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Prognosepreis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragswerte;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ergebnis der Vorschau: was Zusammenfassung und Statuszeilen des Formulars zeigen, wie in {@code berechnet} eines
 * gespeicherten Vertrags. Fehlt eine Angabe für einen Wert, ist er {@code null} (die SPA zeigt „Noch offen“).
 *
 * @param betrag der aktuelle Preis wie am gespeicherten Vertrag; leer ohne vollständige Preiszeile
 * @param preise die vollständigen Preiszeilen, sortiert und mit {@code art} wie {@code preise} eines gespeicherten
 *        Vertrags; leer ohne vollständige Preiszeile
 * @param kostenProMonat braucht Preis und Zahlweise
 * @param eigenerAnteil nur bei einem Anteil unter 100&nbsp;% und mit Preis
 * @param naechstePreisaenderung angekündigt oder Prognose; leer, wenn keine bekannt ist
 * @param prognose die nächsten drei Preise; leer ohne oder mit unvollständiger Preissteigerung
 * @param einmalkosten Summe der vollständigen Einmalkosten; leer ohne solche
 * @param kuendigung wie {@code berechnet.kuendigung} eines gespeicherten Vertrags
 */
record VertragVorschauAntwort(
		@Nullable Betrag betrag,
		List<VertragPreisAntwort> preise,
		@Nullable Betrag kostenProMonat,
		@Nullable VertragVorschauEigenerAnteil eigenerAnteil,
		@Nullable Preisaenderung naechstePreisaenderung,
		List<Prognosepreis> prognose,
		@Nullable Betrag einmalkosten,
		Kuendigung kuendigung) {

	/**
	 * Der eigene Anteil an {@code betrag} und {@code kostenProMonat}; den Anteil am Jahr zeigt das Formular nicht.
	 *
	 * @param kostenProMonat braucht zusätzlich die Zahlweise
	 */
	record VertragVorschauEigenerAnteil(Betrag betrag, @Nullable Betrag kostenProMonat) {
	}

	static VertragVorschauAntwort aus(Vertragswerte werte, Berechnungsgrundlage grundlage) {
		return new VertragVorschauAntwort(werte.betrag(),
				werte.preise().stream().map(VertragPreisAntwort::aus).toList(), werte.kostenProMonat(),
				werte.eigenerAnteilBetrag() != null
						? new VertragVorschauEigenerAnteil(werte.eigenerAnteilBetrag(), werte.eigenerAnteilProMonat())
						: null,
				werte.naechstePreisaenderung(), werte.prognose(),
				grundlage.einmalkosten().isEmpty() ? null : werte.einmalkosten(), werte.kuendigung());
	}

}
