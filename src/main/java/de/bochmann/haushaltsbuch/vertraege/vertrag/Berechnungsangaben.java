package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Die Angaben, aus denen die Berechnung rechnet: Teil der {@link Vertragsangaben} und die Anfrage der Vorschau
 * ({@link VertragVorschauAnfrage}). {@link #daten} und {@link #grundlage} bereinigen sie für beide gleich, damit die
 * Vorschau dasselbe ergibt wie der gespeicherte Vertrag. Die Regeln am Vertrag ({@link PreisNichtVorBeginn},
 * {@link GleicheWaehrung}) prüfen beide.
 */
interface Berechnungsangaben {

	List<? extends Preisangabe> preise();

	Zahlweise zahlweise();

	LocalDate abbuchungAb();

	BigDecimal anteilProzent();

	List<? extends Einmalkostenangabe> einmalkosten();

	Steigerungsangabe preissteigerung();

	LocalDate preisgarantieBis();

	Boolean kuendbar();

	LocalDate beginn();

	LocalDate laufzeitende();

	Verlaengerung verlaengerung();

	Integer kuendigungsfristMonate();

	LocalDate gekuendigtZum();

	/**
	 * Bereinigt: Ein nicht kündbarer Vertrag hat weder Verlängerung noch Kündigungsfrist; Beginn und Laufzeitende
	 * bleiben (#67).
	 */
	default Vertragsdaten daten() {
		boolean kuendbar = kuendbar();
		return new Vertragsdaten(zahlweise(), abbuchungAb(), kuendbar, beginn(), laufzeitende(),
				kuendbar ? verlaengerung() : null, kuendbar ? kuendigungsfristMonate() : null, gekuendigtZum());
	}

	/** Für die Berechnung, nur mit vollständigen Zeilen; beim Speichern sind ohnehin alle vollständig. */
	default Berechnungsgrundlage grundlage() {
		List<Preis> preise = preise() == null ? List.of() : preise().stream()
				.filter(Objects::nonNull).filter(Preisangabe::vollstaendig).<Preis>map(Preisangabe::preis).toList();
		List<? extends Einmalkostenangabe> einmalkosten = einmalkosten() == null ? List.of() : einmalkosten();
		return new Berechnungsgrundlage(daten(), preise.isEmpty() ? null : new Preisliste(preise),
				new Preisprognose(preissteigerung() == null ? null : preissteigerung().regel(), preisgarantieBis()),
				Anteil.aus(anteilProzent()),
				einmalkosten.stream().filter(Objects::nonNull).filter(Einmalkostenangabe::vollstaendig)
						.map(Einmalkostenangabe::einmalkostenFuerBerechnung).toList());
	}

}
