package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preiszeilenart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;

/** Die Vorschau mit halb ausgefüllten Formularen; ohne Spring, sie liest keine gespeicherten Verträge. */
class VertragVorschauServiceTests {

	private final Clock clock = Clock.system(ZoneId.of("Europe/Berlin"));

	private final VertragVorschauService service = new VertragVorschauService(clock);

	@Test
	void vorschauMitFehlendenWerten() {
		var leer = new VertragVorschauAnfrage(null, null, null, null, null, null, null, true, null, null, null, null, null, null);

		var vorschau = service.vorschau(leer);

		assertThat(vorschau.kuendigung().art()).isEqualTo(Kuendigungsart.KEINE_LAUFZEIT);
		assertThat(vorschau).isEqualTo(new VertragVorschauAntwort(null, List.of(), null, null, null, List.of(), null,
				vorschau.kuendigung(), null));
	}

	/** Was fehlt, bleibt leer; der Rest wird trotzdem berechnet. */
	@Test
	void vorschauMitTeilweisenAngaben() {
		LocalDate heute = LocalDate.now(clock);
		var preis = List.of(new VertragVorschauPreis(heute, euro("10.00"), null));
		var ohneBeginn = new VertragVorschauAnfrage(preis, Zahlweise.MONATLICH, new BigDecimal("50"), null, null,
				null, null, true, null, null, null, null, null, null);
		var ohneZahlweise = new VertragVorschauAnfrage(preis, null, new BigDecimal("50"), null,
				null, null, null, true, heute, null, null, null, null, null);

		var ohneBeginnVorschau = service.vorschau(ohneBeginn);
		var ohneZahlweiseVorschau = service.vorschau(ohneZahlweise);

		// Ohne Beginn keine Preise: Der erste gilt ab dem Beginn, spätere gälten sonst rückwirkend.
		assertThat(ohneBeginnVorschau.betrag()).isNull();
		assertThat(ohneBeginnVorschau.preise()).isEmpty();
		assertThat(ohneBeginnVorschau.kostenProMonat()).isNull();
		assertThat(ohneBeginnVorschau.eigenerAnteil()).isNull();
		assertThat(ohneBeginnVorschau.kuendigung().art()).isEqualTo(Kuendigungsart.KEINE_LAUFZEIT);
		assertThat(ohneZahlweiseVorschau.betrag()).isEqualTo(euro("10.00"));
		assertThat(ohneZahlweiseVorschau.kostenProMonat()).isNull();
		// Der Anteil am Betrag braucht nur Preis und Anteil, der pro Monat zusätzlich die Zahlweise.
		assertThat(ohneZahlweiseVorschau.eigenerAnteil())
				.isEqualTo(new VertragVorschauAntwort.VertragVorschauEigenerAnteil(euro("5.00"), null));
	}

	/** Unvollständige Zeilen ignoriert die Vorschau, statt sie abzulehnen. */
	@Test
	void vorschauIgnoriertUnvollstaendigeZeilen() {
		LocalDate heute = LocalDate.now(clock);
		var preise = List.of(new VertragVorschauPreis(heute, euro("10.00"), null), new VertragVorschauPreis(heute.plusDays(5), null, null),
				new VertragVorschauPreis(null, euro("11.00"), null));
		var einmalkosten = List.of(new VertragVorschauEinmalkosten("Router", euro("99.99"), null),
				new VertragVorschauEinmalkosten(" ", euro("5.00"), heute),
				new VertragVorschauEinmalkosten("Anschluss", euro("49.00"), heute));
		// Prozent ohne Prozentsatz; der Betrag gehört zur anderen Art und zählt nicht.
		var steigerung = new VertragVorschauPreissteigerung(Steigerungsart.PROZENT, null, euro("1.00"),
				Erhoehungsrhythmus.JAEHRLICH, heute);

		var vorschau = service.vorschau(new VertragVorschauAnfrage(preise, Zahlweise.MONATLICH,
				null, einmalkosten, steigerung, null, null, true, heute, null, null, null, null, null));

		assertThat(vorschau.betrag()).isEqualTo(euro("10.00"));
		assertThat(vorschau.preise())
				.containsExactly(new VertragVorschauPreisAntwort(heute, euro("10.00"), Preiszeilenart.AKTUELL, false, false));
		assertThat(vorschau.naechstePreisaenderung()).isNull();
		assertThat(vorschau.prognose()).isEmpty();
		assertThat(vorschau.einmalkosten()).isEqualTo(euro("49.00"));
	}

	/** Ohne Preis summieren die Einmalkosten in ihrer eigenen Währung; ohne vollständige Zeile bleiben sie leer. */
	@Test
	void vorschauEinmalkostenOhnePreis() {
		var nurEinmalkosten = new VertragVorschauAnfrage(null, null, null,
				List.of(new VertragVorschauEinmalkosten("Router", euro("99.99"), LocalDate.now(clock))), null, null, null, true,
				null, null, null, null, null, null);
		var nichtsVollstaendig = new VertragVorschauAnfrage(List.of(new VertragVorschauPreis(null, euro("10.00"), null)), null,
				null, List.of(new VertragVorschauEinmalkosten("Router", null, null)), null, null, null, true, null, null,
				null, null, null, null);

		assertThat(service.vorschau(nurEinmalkosten).einmalkosten()).isEqualTo(euro("99.99"));
		assertThat(service.vorschau(nurEinmalkosten).betrag()).isNull();
		assertThat(service.vorschau(nichtsVollstaendig).einmalkosten()).isNull();
	}

}
