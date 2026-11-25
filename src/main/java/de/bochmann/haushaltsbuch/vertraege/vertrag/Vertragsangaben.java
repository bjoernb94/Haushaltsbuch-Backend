package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Berechnungsangaben;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Die Angaben, die die SPA zu einem Vertrag schickt, beim Anlegen ({@link VertragAnfrage}) wie beim
 * Ändern ({@link VertragAenderung}). Bedeutung der Felder siehe {@link VertragAnfrage}; die für die Berechnung
 * erbt es von {@link Berechnungsangaben}.
 * <p>
 * {@code public}, obwohl nur dieses Paket es nutzt: Es engt die Typen von {@code preise}, {@code einmalkosten} und
 * {@code preissteigerung} auf die Anfragen ein. Ist es package-private, beschreibt springdoc (ab 3.2.1, swagger-core
 * 3) diese Felder mit den Typen des öffentlichen {@link Berechnungsangaben}, also als leere Schemas
 * {@code Preisangabe} usw.
 */
public interface Vertragsangaben extends Berechnungsangaben {

	String anbieter();

	String bezeichnung();

	UUID kategorieId();

	String vertragsnummer();

	List<VertragPreisAnfrage> preise();

	Zahlungsweg zahlungsweg();

	BigDecimal anteilProzent();

	List<VertragEinmalkostenAnfrage> einmalkosten();

	VertragPreissteigerungAnfrage preissteigerung();

	LocalDate preisgarantieBis();

	Boolean erinnerungSonderkuendigung();

	String notiz();

}
