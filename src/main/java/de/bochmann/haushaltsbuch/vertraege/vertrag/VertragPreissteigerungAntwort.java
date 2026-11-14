package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Steigerungsart;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Die Regel der Preissteigerung in der Antwort, wie in {@link VertragPreissteigerungAnfrage}. */
record VertragPreissteigerungAntwort(
		Steigerungsart art,
		@Nullable BigDecimal prozent,
		@Nullable Betrag betrag,
		Erhoehungsrhythmus rhythmus,
		LocalDate naechsteAm) {

	static @Nullable VertragPreissteigerungAntwort aus(@Nullable VertragPreissteigerung steigerung) {
		return steigerung == null ? null : new VertragPreissteigerungAntwort(steigerung.art(), steigerung.prozent(),
				steigerung.betrag(), steigerung.rhythmus(), steigerung.naechsteAm());
	}

}
