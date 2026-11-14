package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Abbuchung;
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
 * @param betrag der heute gültige Preis; leer ohne vollständige Preiszeile
 * @param kostenProMonat braucht Preis und Zahlweise
 * @param eigenerAnteil nur bei einem Anteil unter 100&nbsp;% und mit {@code kostenProMonat}
 * @param naechsteAbbuchung braucht Zahlweise, {@code abbuchungAb} und Preis; leer auch, wenn keine mehr kommt
 * @param naechstePreisaenderung angekündigt oder Prognose; leer, wenn keine bekannt ist
 * @param prognose die nächsten drei Preise; leer ohne oder mit unvollständiger Preissteigerung
 * @param einmalkosten Summe der vollständigen Einmalkosten; leer ohne solche
 * @param kuendigung wie {@code berechnet.kuendigung} eines gespeicherten Vertrags
 */
record VertragVorschauAntwort(
		@Nullable Betrag betrag,
		@Nullable Betrag kostenProMonat,
		@Nullable VertragVorschauEigenerAnteil eigenerAnteil,
		@Nullable Abbuchung naechsteAbbuchung,
		@Nullable Preisaenderung naechstePreisaenderung,
		List<Prognosepreis> prognose,
		@Nullable Betrag einmalkosten,
		Kuendigung kuendigung) {

	/** Der eigene Anteil an {@code kostenProMonat}; den Anteil am Jahr zeigt das Formular nicht. */
	record VertragVorschauEigenerAnteil(Betrag kostenProMonat) {
	}

	static VertragVorschauAntwort aus(Vertragswerte werte, Berechnungsgrundlage grundlage) {
		return new VertragVorschauAntwort(werte.betrag(), werte.kostenProMonat(),
				werte.eigenerAnteilProMonat() != null ? new VertragVorschauEigenerAnteil(werte.eigenerAnteilProMonat())
						: null,
				werte.naechsteAbbuchung(), werte.naechstePreisaenderung(), werte.prognose(),
				grundlage.einmalkosten().isEmpty() ? null : werte.einmalkosten(), werte.kuendigung());
	}

}
