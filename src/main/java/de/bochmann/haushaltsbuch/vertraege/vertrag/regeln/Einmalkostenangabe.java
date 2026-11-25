package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Einmalkosten;

import java.time.LocalDate;

/**
 * Eine Zeile der Einmalkosten, beim Speichern ({@code VertragEinmalkostenAnfrage}) wie in der Vorschau
 * ({@code VertragVorschauEinmalkosten}).
 */
public interface Einmalkostenangabe {

	String bezeichnung();

	Betrag betrag();

	LocalDate faelligAm();

	/** Ohne Bezeichnung, Betrag oder Fälligkeit unvollständig: Die Vorschau ignoriert die Zeile. */
	default boolean vollstaendig() {
		return bezeichnung() != null && !bezeichnung().isBlank() && betrag() != null && faelligAm() != null;
	}

	default Einmalkosten einmalkostenFuerBerechnung() {
		return new Einmalkosten(faelligAm(), betrag());
	}

}
