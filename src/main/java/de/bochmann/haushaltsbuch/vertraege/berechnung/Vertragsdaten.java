package de.bochmann.haushaltsbuch.vertraege.berechnung;

import java.time.LocalDate;

/**
 * Die Angaben eines Vertrags, die die Berechnung braucht. Alle Werte dürfen fehlen, damit auch die
 * Vorschau eines halb ausgefüllten Formulars (#41) rechnen kann.
 *
 * @param abbuchungAb ab diesem Tag wird im Takt der Zahlweise abgebucht
 * @param gekuendigtZum gesetzt, wenn der Vertrag gekündigt ist
 */
public record Vertragsdaten(
		Zahlweise zahlweise,
		LocalDate abbuchungAb,
		boolean kuendbar,
		LocalDate beginn,
		LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		Integer kuendigungsfristMonate,
		LocalDate gekuendigtZum) {
}
