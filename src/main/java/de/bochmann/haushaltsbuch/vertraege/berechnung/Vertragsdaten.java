package de.bochmann.haushaltsbuch.vertraege.berechnung;

import java.time.LocalDate;

/**
 * Die Angaben eines Vertrags, die die Berechnung braucht. Alle Werte dürfen fehlen, damit auch die
 * Vorschau eines halb ausgefüllten Formulars (#41) rechnen kann.
 *
 * @param beginn Vertragsbeginn und Anker des Zahlungsrhythmus: ab diesem Tag wird im Takt der Zahlweise abgebucht;
 *        fehlt nur in der Vorschau
 * @param gekuendigtZum gesetzt, wenn der Vertrag gekündigt ist
 */
public record Vertragsdaten(
		Zahlweise zahlweise,
		boolean kuendbar,
		LocalDate beginn,
		LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		Integer kuendigungsfristMonate,
		LocalDate gekuendigtZum) {
}
