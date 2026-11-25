package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

/** Unvollständige Regeln (in der Vorschau erlaubt) ergeben keine Prognose; der Wert der anderen Art zählt nicht. */
class SteigerungsangabeTests {

	private static final LocalDate NAECHSTE = LocalDate.of(2027, 1, 1);

	private static final Betrag EIN_EURO = new Betrag(new BigDecimal("1.00"), Currency.getInstance("EUR"));

	/** Wie die Preissteigerung der Vorschau: Alle Angaben dürfen fehlen. */
	private record Angabe(Steigerungsart art, BigDecimal prozent, Betrag betrag, Erhoehungsrhythmus rhythmus,
			LocalDate naechsteAm) implements Steigerungsangabe {
	}

	@Test
	void ohneWertZurArtKeineRegel() {
		assertThat(new Angabe(Steigerungsart.BETRAG, new BigDecimal("2.5"), null,
				Erhoehungsrhythmus.JAEHRLICH, NAECHSTE).regel()).isNull();
		assertThat(new Angabe(Steigerungsart.PROZENT, null, EIN_EURO,
				Erhoehungsrhythmus.JAEHRLICH, NAECHSTE).regel()).isNull();
	}

	@Test
	void ohneArtRhythmusOderTerminKeineRegel() {
		assertThat(new Angabe(null, new BigDecimal("2.5"), null,
				Erhoehungsrhythmus.JAEHRLICH, NAECHSTE).regel()).isNull();
		assertThat(new Angabe(Steigerungsart.PROZENT, new BigDecimal("2.5"), null,
				null, NAECHSTE).regel()).isNull();
		assertThat(new Angabe(Steigerungsart.PROZENT, new BigDecimal("2.5"), null,
				Erhoehungsrhythmus.JAEHRLICH, null).regel()).isNull();
	}

	@Test
	void wertDerAnderenArtZaehltNicht() {
		var regel = new Angabe(Steigerungsart.BETRAG, new BigDecimal("2.5"), EIN_EURO,
				Erhoehungsrhythmus.JAEHRLICH, NAECHSTE).regel();

		assertThat(regel).isNotNull();
		assertThat(regel.prozent()).isNull();
		assertThat(regel.betrag()).isEqualTo(EIN_EURO);
	}

}
