package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

import java.time.LocalDate;

/**
 * Eine Zeit, in der der Vertrag nicht lief und nichts abgebucht wurde (ADR 0023). Preise und Erhöhungen gelten nach
 * Kalender weiter. Unterbrechungen überschneiden sich nicht und liegen nach dem Beginn; zwischen ihnen, dem Beginn
 * und dem Vertragsende liegen die Phasen, in denen der Vertrag lief.
 *
 * @param von erster Tag der Unterbrechung, der Tag nach dem früheren Vertragsende
 * @param bis letzter Tag der Unterbrechung, der Tag vor der Wiederaufnahme
 */
public record Unterbrechung(LocalDate von, LocalDate bis, Unterbrechungsart art) {

	/** Der erste Tag der Phase danach: der Tag der Wiederaufnahme. */
	public LocalDate wiederAb() {
		return bis.plusDays(1);
	}

	/** Ob {@code tag} in der Unterbrechung liegt, beide Enden eingeschlossen. */
	public boolean enthaelt(LocalDate tag) {
		return !tag.isBefore(von) && !tag.isAfter(bis);
	}

}
