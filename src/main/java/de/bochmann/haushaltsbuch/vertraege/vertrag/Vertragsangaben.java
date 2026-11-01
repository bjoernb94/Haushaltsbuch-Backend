package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Die Angaben, die die SPA zu einem Vertrag schickt, beim Anlegen ({@link VertragAnfrage}) wie beim
 * Ändern ({@link VertragAenderung}). Bedeutung der Felder siehe {@link VertragAnfrage}.
 */
interface Vertragsangaben {

	String anbieter();

	String bezeichnung();

	UUID kategorieId();

	String vertragsnummer();

	Betrag betrag();

	Zahlweise zahlweise();

	LocalDate abbuchungAb();

	Zahlungsweg zahlungsweg();

	Boolean kuendbar();

	LocalDate beginn();

	LocalDate laufzeitende();

	Verlaengerung verlaengerung();

	Integer kuendigungsfristMonate();

	LocalDate gekuendigtZum();

	String notiz();

}
