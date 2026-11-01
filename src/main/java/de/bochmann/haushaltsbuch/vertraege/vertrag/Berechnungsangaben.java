package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;

import java.time.LocalDate;

/**
 * Die Angaben, aus denen die Berechnung rechnet: Teil der {@link Vertragsangaben} und die Anfrage der Vorschau
 * ({@link VertragVorschauAnfrage}). {@link #daten} bereinigt sie für beide gleich, damit die Vorschau dasselbe
 * ergibt wie der gespeicherte Vertrag.
 */
interface Berechnungsangaben {

	Zahlweise zahlweise();

	LocalDate abbuchungAb();

	Boolean kuendbar();

	LocalDate beginn();

	LocalDate laufzeitende();

	Verlaengerung verlaengerung();

	Integer kuendigungsfristMonate();

	LocalDate gekuendigtZum();

	/** Bereinigt: Ein nicht kündbarer Vertrag hat weder Verlängerung noch Kündigungsfrist. */
	default Vertragsdaten daten() {
		boolean kuendbar = kuendbar();
		return new Vertragsdaten(zahlweise(), abbuchungAb(), kuendbar, beginn(), laufzeitende(),
				kuendbar ? verlaengerung() : null, kuendbar ? kuendigungsfristMonate() : null, gekuendigtZum());
	}

}
