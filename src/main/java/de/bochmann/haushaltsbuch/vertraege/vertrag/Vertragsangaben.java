package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.util.UUID;

/**
 * Die Angaben, die die SPA zu einem Vertrag schickt, beim Anlegen ({@link VertragAnfrage}) wie beim
 * Ändern ({@link VertragAenderung}). Bedeutung der Felder siehe {@link VertragAnfrage}; die für die Berechnung
 * erbt es von {@link Berechnungsangaben}.
 */
interface Vertragsangaben extends Berechnungsangaben {

	String anbieter();

	String bezeichnung();

	UUID kategorieId();

	String vertragsnummer();

	Betrag betrag();

	Zahlungsweg zahlungsweg();

	String notiz();

}
