package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Unterbrechung;

import java.time.LocalDate;
import java.util.List;

/**
 * Die Regeln, damit die Angaben eines Vertrags zu seinen Unterbrechungen passen (ADR 0023). Beim Ändern prüft sie der
 * Service gegen die verbleibenden gespeicherten Unterbrechungen, in der Vorschau die Constraints
 * ({@link BeginnVorUnterbrechung}, {@link KuendigungNachUnterbrechung}, {@link LaufzeitendeNachAktuellemBeginn},
 * {@link UnterbrechungenGetrennt}) gegen die mitgeschickten. Ohne Unterbrechungen ist alles erlaubt; dann gelten nur
 * die Regeln am Vertrag wie {@link LaufzeitendeNachBeginn}.
 */
final class Unterbrechungsregeln {

	private Unterbrechungsregeln() {
	}

	/** Der Beginn liegt vor der ersten Unterbrechung. */
	static boolean beginnVorUnterbrechung(LocalDate beginn, List<Unterbrechung> unterbrechungen) {
		return beginn == null || unterbrechungen.isEmpty() || beginn.isBefore(unterbrechungen.getFirst().von());
	}

	/** Gekündigt frühestens zum Tag der letzten Wiederaufnahme: Die letzte Phase dauert mindestens einen Tag. */
	static boolean kuendigungNachUnterbrechung(LocalDate gekuendigtZum, List<Unterbrechung> unterbrechungen) {
		return gekuendigtZum == null || unterbrechungen.isEmpty()
				|| !gekuendigtZum.isBefore(unterbrechungen.getLast().wiederAb());
	}

	/** Das Laufzeitende liegt nicht vor dem aktuellen Beginn, dem Tag der letzten Wiederaufnahme. */
	static boolean laufzeitendeNachAktuellemBeginn(LocalDate laufzeitende, List<Unterbrechung> unterbrechungen) {
		return laufzeitende == null || unterbrechungen.isEmpty()
				|| !laufzeitende.isBefore(unterbrechungen.getLast().wiederAb());
	}

	/**
	 * Index der ersten Unterbrechung, die nicht passt: {@code bis} vor {@code von}, oder sie beginnt nicht nach der
	 * Phase vor ihr (zwischen zwei Unterbrechungen läuft der Vertrag mindestens einen Tag); -1, wenn alle passen.
	 *
	 * @param unterbrechungen sortiert nach {@code von}
	 */
	static int ersteUeberschneidung(List<Unterbrechung> unterbrechungen) {
		for (int i = 0; i < unterbrechungen.size(); i++) {
			var unterbrechung = unterbrechungen.get(i);
			if (unterbrechung.bis().isBefore(unterbrechung.von())
					|| (i > 0 && !unterbrechung.von().isAfter(unterbrechungen.get(i - 1).wiederAb()))) {
				return i;
			}
		}
		return -1;
	}

}
