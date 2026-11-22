package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Alle berechneten Werte eines Vertrags zum Stichtag, siehe {@link Vertragsberechnung#werte}. Ein Wert ist
 * {@code null}, wenn eine Angabe dafür fehlt; beim gespeicherten Vertrag ist er es nur, wo auch die API {@code null}
 * kennt (z.&nbsp;B. kein Vertragsende).
 *
 * @param betrag der {@link Vertragsberechnung#aktuellerPreis aktuelle Preis}; braucht einen Preis
 * @param betragErrechnet ob {@code betrag} eine seit dem letzten Speichern fällige Stufe der Preissteigerung ist
 * @param preise die gespeicherte Preisliste mit dem Eintrag von {@code betrag}; ohne Preis leer
 * @param kostenProMonat braucht Preis und Zahlweise
 * @param kostenImJahr braucht Preis, Zahlweise und {@code beginn}
 * @param eigenerAnteilBetrag Anteil an {@code betrag}; nur bei einem Anteil unter 100&nbsp;% und mit Preis
 * @param eigenerAnteilProMonat nur bei einem Anteil unter 100&nbsp;% und mit {@code kostenProMonat}
 * @param eigenerAnteilImJahr nur bei einem Anteil unter 100&nbsp;% und mit {@code kostenImJahr}
 * @param einmalkosten Summe aller Einmalkosten; ohne Einträge 0 in der Währung der Preise, ohne beides leer
 * @param vertragsende letzter Vertragstag ({@code gekuendigtZum} bzw. Laufzeitende ohne Verlängerung, das frühere);
 *        leer bei offenem Ende
 */
public record Vertragswerte(
		Vertragsstatus status,
		@Nullable Betrag betrag,
		boolean betragErrechnet,
		List<Preiszeile> preise,
		@Nullable Betrag kostenProMonat,
		@Nullable Betrag kostenImJahr,
		boolean kostenImJahrMitPrognose,
		@Nullable Betrag eigenerAnteilBetrag,
		@Nullable Betrag eigenerAnteilProMonat,
		@Nullable Betrag eigenerAnteilImJahr,
		@Nullable Betrag einmalkosten,
		@Nullable LocalDate vertragsende,
		@Nullable Preisaenderung naechstePreisaenderung,
		List<Prognosepreis> prognose,
		List<Preispunkt> preisverlauf,
		Kuendigung kuendigung) {
}
