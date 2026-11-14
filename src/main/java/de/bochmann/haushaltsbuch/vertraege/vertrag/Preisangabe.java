package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preis;

import java.time.LocalDate;

/**
 * Eine Zeile der Preisliste, beim Speichern ({@link VertragPreisAnfrage}) wie in der Vorschau
 * ({@link VertragVorschauPreis}); die Regeln über die Liste ({@link GueltigAbPflicht} …) prüfen beide gleich.
 */
interface Preisangabe {

	LocalDate gueltigAb();

	Betrag betrag();

	/** Ohne Betrag ist die Zeile unvollständig: Die Vorschau ignoriert sie, beim Speichern ist sie ungültig. */
	default boolean vollstaendig() {
		return betrag() != null;
	}

	default Preis preis() {
		return new Preis(gueltigAb(), betrag());
	}

}
