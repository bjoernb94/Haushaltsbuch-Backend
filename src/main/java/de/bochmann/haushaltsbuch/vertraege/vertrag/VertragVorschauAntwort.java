package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Prognosepreis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragswerte;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ergebnis der Vorschau: was Zusammenfassung und Statuszeilen des Formulars zeigen, wie in {@code berechnet} eines
 * gespeicherten Vertrags. Fehlt eine Angabe für einen Wert, ist er {@code null} (die SPA zeigt „Noch offen“).
 *
 * @param betrag der aktuelle Preis wie am gespeicherten Vertrag; leer ohne {@code beginn} oder vollständige Preiszeile
 * @param preise die vollständigen Preiszeilen, wie sie nach dem Speichern wären: sortiert und mit {@code art} wie
 *        {@code preise} eines gespeicherten Vertrags, dazu mit {@code neu} die fälligen Stufen der Preissteigerung,
 *        die das Speichern anlegt; leer ohne {@code beginn} oder vollständige Preiszeile
 * @param kostenProMonat braucht Preis und Zahlweise
 * @param eigenerAnteil nur bei einem Anteil unter 100&nbsp;% und mit Preis
 * @param naechstePreisaenderung angekündigt oder Prognose; leer, wenn keine bekannt ist
 * @param prognose die nächsten drei Preise; leer ohne oder mit unvollständiger Preissteigerung
 * @param einmalkosten Summe der vollständigen Einmalkosten; leer ohne solche
 * @param kuendigung wie {@code berechnet.kuendigung} eines gespeicherten Vertrags
 * @param vorschlagNaechsteAm Vorschlag für {@code preissteigerung.naechsteAm}: {@code beginn} plus einen Rhythmus;
 *        leer ohne {@code beginn} oder {@code preissteigerung.rhythmus}
 */
record VertragVorschauAntwort(
		@Nullable Betrag betrag,
		List<VertragVorschauPreisAntwort> preise,
		@Nullable Betrag kostenProMonat,
		@Nullable VertragVorschauEigenerAnteil eigenerAnteil,
		@Nullable Preisaenderung naechstePreisaenderung,
		List<Prognosepreis> prognose,
		@Nullable Betrag einmalkosten,
		Kuendigung kuendigung,
		@Nullable LocalDate vorschlagNaechsteAm) {

	/**
	 * Der eigene Anteil an {@code betrag} und {@code kostenProMonat}; den Anteil am Jahr zeigt das Formular nicht.
	 *
	 * @param kostenProMonat braucht zusätzlich die Zahlweise
	 */
	record VertragVorschauEigenerAnteil(Betrag betrag, @Nullable Betrag kostenProMonat) {
	}

	/**
	 * @param grundlage wie nach dem Speichern, siehe {@code Vertragsberechnung#wieGespeichert}
	 * @param anfrage für {@code neu} und den Vorschlag
	 */
	static VertragVorschauAntwort aus(Vertragswerte werte, Berechnungsgrundlage grundlage,
			VertragVorschauAnfrage anfrage) {
		var angefragt = anfrage.grundlage().preise() == null ? Set.<LocalDate>of()
				: anfrage.grundlage().preise().preise().stream().map(Preis::gueltigAb).collect(Collectors.toSet());
		var steigerung = anfrage.preissteigerung();
		var vorschlag = anfrage.beginn() != null && steigerung != null && steigerung.rhythmus() != null
				? steigerung.rhythmus().nach(anfrage.beginn())
				: null;
		return new VertragVorschauAntwort(werte.betrag(),
				werte.preise().stream().map(preis -> VertragVorschauPreisAntwort.aus(preis, angefragt)).toList(),
				werte.kostenProMonat(),
				werte.eigenerAnteilBetrag() != null
						? new VertragVorschauEigenerAnteil(werte.eigenerAnteilBetrag(), werte.eigenerAnteilProMonat())
						: null,
				werte.naechstePreisaenderung(), werte.prognose(),
				grundlage.einmalkosten().isEmpty() ? null : werte.einmalkosten(), werte.kuendigung(), vorschlag);
	}

}
