package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Alle berechneten Werte eines Vertrags zum Stichtag, siehe {@link Vertragsberechnung#werte}. Ein Wert ist
 * {@code null}, wenn eine Angabe dafür fehlt; beim gespeicherten Vertrag ist er es nur, wo auch die API {@code null}
 * kennt (z.&nbsp;B. keine nächste Abbuchung mehr).
 *
 * @param betrag der heute gültige Preis; braucht einen Preis
 * @param kostenProMonat braucht Preis und Zahlweise
 * @param kostenImJahr braucht Preis, Zahlweise und {@code abbuchungAb}
 * @param eigenerAnteilProMonat nur bei einem Anteil unter 100&nbsp;% und mit {@code kostenProMonat}
 * @param eigenerAnteilImJahr nur bei einem Anteil unter 100&nbsp;% und mit {@code kostenImJahr}
 * @param einmalkosten Summe aller Einmalkosten; ohne Einträge 0 in der Währung der Preise, ohne beides leer
 */
public record Vertragswerte(
		Vertragsstatus status,
		@Nullable Betrag betrag,
		@Nullable Betrag kostenProMonat,
		@Nullable Betrag kostenImJahr,
		boolean kostenImJahrMitPrognose,
		@Nullable Betrag eigenerAnteilProMonat,
		@Nullable Betrag eigenerAnteilImJahr,
		@Nullable Betrag einmalkosten,
		@Nullable Abbuchung naechsteAbbuchung,
		@Nullable Preisaenderung naechstePreisaenderung,
		List<Prognosepreis> prognose,
		List<Preispunkt> preisverlauf,
		Kuendigung kuendigung) {
}
