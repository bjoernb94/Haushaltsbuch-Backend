package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Einmalkosten;

import java.time.LocalDate;

/**
 * Eine Zeile der Einmalkosten, beim Speichern ({@link VertragEinmalkostenAnfrage}) wie in der Vorschau
 * ({@link VertragVorschauEinmalkosten}).
 */
interface Einmalkostenangabe {

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
