package de.bochmann.haushaltsbuch.vertraege.berechnung;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Die Angaben eines Vertrags, die die Berechnung braucht. Alle Werte dürfen fehlen, damit auch die
 * Vorschau eines halb ausgefüllten Formulars (#41) rechnen kann.
 *
 * @param beginn Vertragsbeginn und Anker des Zahlungsrhythmus: ab diesem Tag wird im Takt der Zahlweise abgebucht;
 *        fehlt nur in der Vorschau
 * @param gekuendigtZum gesetzt, wenn der Vertrag gekündigt ist
 * @param unterbrechungen sortiert, ohne Überschneidung, alle nach dem Beginn; leer, wenn der Vertrag durchgehend lief
 */
public record Vertragsdaten(
		Zahlweise zahlweise,
		boolean kuendbar,
		LocalDate beginn,
		LocalDate laufzeitende,
		Verlaengerung verlaengerung,
		Integer kuendigungsfristMonate,
		LocalDate gekuendigtZum,
		List<Unterbrechung> unterbrechungen) {

	public Vertragsdaten {
		unterbrechungen = unterbrechungen == null ? List.of()
				: unterbrechungen.stream().sorted(Comparator.comparing(Unterbrechung::von)).toList();
	}

	/** Ohne Unterbrechungen. */
	public Vertragsdaten(Zahlweise zahlweise, boolean kuendbar, LocalDate beginn, LocalDate laufzeitende,
			Verlaengerung verlaengerung, Integer kuendigungsfristMonate, LocalDate gekuendigtZum) {
		this(zahlweise, kuendbar, beginn, laufzeitende, verlaengerung, kuendigungsfristMonate, gekuendigtZum, List.of());
	}

	/**
	 * Der Beginn der letzten Phase: nach einer Unterbrechung der Tag der Wiederaufnahme, sonst {@code beginn}. Ab ihm
	 * zählen Laufzeit und Verlängerung. Leer ohne {@code beginn} (nur in der Vorschau).
	 */
	public LocalDate aktuellerBeginn() {
		return unterbrechungen.isEmpty() ? beginn : unterbrechungen.getLast().wiederAb();
	}

}
