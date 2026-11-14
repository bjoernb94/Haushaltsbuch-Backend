package de.bochmann.haushaltsbuch.vertraege.vertrag;

import java.util.List;
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

	List<VertragPreisAnfrage> preise();

	Zahlungsweg zahlungsweg();

	String notiz();

}
