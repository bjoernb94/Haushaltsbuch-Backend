package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;

import java.time.LocalDate;

/**
 * Eine Unterbrechung, wie die Vorschau sie mitschickt ({@code VertragVorschauUnterbrechung}); die Regeln über die Liste
 * ({@link UnterbrechungenGetrennt}) prüfen sie. Gespeicherte Unterbrechungen legt nur die Wiederaufnahme an.
 */
public interface Unterbrechungsangabe {

	LocalDate von();

	LocalDate bis();

	Unterbrechungsart art();

	/** Ohne {@code von}, {@code bis} oder {@code art} unvollständig: Die Vorschau ignoriert die Zeile. */
	default boolean vollstaendig() {
		return von() != null && bis() != null && art() != null;
	}

	default Unterbrechung unterbrechung() {
		return new Unterbrechung(von(), bis(), art());
	}

}
