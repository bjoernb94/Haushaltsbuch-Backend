package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Die Regel der regelmäßigen Preissteigerung, als Spalten {@code preissteigerung_*} am Vertrag gespeichert; alle
 * leer ohne Regel. In der API {@link VertragPreissteigerungAnfrage} bzw. {@link VertragPreissteigerungAntwort}.
 */
@Embeddable
record VertragPreissteigerung(
		@Enumerated(EnumType.STRING) @Column(length = 40)
		Steigerungsart art,
		@Column(precision = 5, scale = 2)
		BigDecimal prozent,
		@Embedded
		Betrag betrag,
		@Enumerated(EnumType.STRING) @Column(length = 40)
		Erhoehungsrhythmus rhythmus,
		LocalDate naechsteAm) {

	Preissteigerung preissteigerung() {
		return new Preissteigerung(art, prozent, betrag, rhythmus, naechsteAm);
	}

}
