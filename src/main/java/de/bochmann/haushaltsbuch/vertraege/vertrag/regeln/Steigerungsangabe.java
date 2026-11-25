package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Die Regel „Preis steigt regelmäßig“, beim Speichern ({@code VertragPreissteigerungAnfrage}) wie in der Vorschau
 * ({@code VertragVorschauPreissteigerung}).
 */
public interface Steigerungsangabe {

	Steigerungsart art();

	BigDecimal prozent();

	Betrag betrag();

	Erhoehungsrhythmus rhythmus();

	LocalDate naechsteAm();

	/**
	 * Die Regel für die Berechnung; leer, wenn sie unvollständig ist (Art, Rhythmus, nächste Erhöhung oder der Wert zur
	 * Art fehlt). Der Wert der anderen Art zählt nicht.
	 */
	default @Nullable Preissteigerung regel() {
		if (art() == null || rhythmus() == null || naechsteAm() == null) {
			return null;
		}
		return switch (art()) {
			case PROZENT -> prozent() == null ? null
					: new Preissteigerung(art(), prozent(), null, rhythmus(), naechsteAm());
			case BETRAG -> betrag() == null ? null : new Preissteigerung(art(), null, betrag(), rhythmus(), naechsteAm());
		};
	}

}
