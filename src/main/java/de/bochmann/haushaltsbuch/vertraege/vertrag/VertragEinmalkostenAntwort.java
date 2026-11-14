package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;

/** Ein Eintrag der Einmalkosten in der Antwort, wie in {@link VertragEinmalkostenAnfrage}. */
record VertragEinmalkostenAntwort(String bezeichnung, Betrag betrag, LocalDate faelligAm) {

	static VertragEinmalkostenAntwort aus(VertragEinmalkosten einmalkosten) {
		return new VertragEinmalkostenAntwort(einmalkosten.bezeichnung(), einmalkosten.betrag(),
				einmalkosten.faelligAm());
	}

}
