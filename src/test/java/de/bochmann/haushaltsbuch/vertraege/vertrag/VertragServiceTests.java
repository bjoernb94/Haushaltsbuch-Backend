package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.FachmodulTest;
import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.UUID;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@FachmodulTest
class VertragServiceTests {

	/** Standardkategorie {@code VERSICHERUNG} aus der Migration. */
	static final UUID VERSICHERUNG = UUID.fromString("7cb7a2b8-38a6-432d-8546-03b3d74f76ec");

	@Autowired
	private VertragService service;

	@Autowired
	private JdbcClient jdbc;

	private final UUID haushalt = UUID.randomUUID();

	static VertragAnfrage anfrage(UUID kategorieId) {
		return new VertragAnfrage("Musterversicherung AG", "Kfz-Versicherung", kategorieId, "KFZ-4711", euro("586.80"),
				Zahlweise.JAEHRLICH, LocalDate.of(2027, 1, 1), Zahlungsweg.LASTSCHRIFT, true, LocalDate.of(2023, 1, 1),
				LocalDate.of(2023, 12, 31), Verlaengerung.ZWOELF_MONATE, 1, null, "Notiz");
	}

	@Test
	void legtVertragImAktuellenHaushaltAn() {
		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThat(jdbc.sql("SELECT haushalt_id FROM vertrag WHERE id = ?").param(vertrag.id()).query(UUID.class).single())
				.isEqualTo(haushalt);
		assertThat(vertrag.kategorie().schluessel()).isEqualTo("VERSICHERUNG");
		assertThat(vertrag.version()).isZero();
		assertThat(vertrag.berechnet().kostenProMonat()).isEqualTo(euro("48.90"));
	}

	@Test
	void leertUnpassendeAngaben() {
		var rundfunk = new VertragAnfrage("  Beitragsservice  ", " ", VERSICHERUNG, "", euro("55.08"),
				Zahlweise.VIERTELJAEHRLICH, LocalDate.of(2026, 11, 15), null, false, LocalDate.of(2020, 1, 1), null,
				Verlaengerung.MONATLICH, 3, null, "  ");

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(rundfunk));

		assertThat(vertrag.anbieter()).isEqualTo("Beitragsservice");
		assertThat(vertrag.bezeichnung()).isNull();
		assertThat(vertrag.vertragsnummer()).isNull();
		assertThat(vertrag.notiz()).isNull();
		// Nicht kündbar: keine Verlängerung und Frist; der Beginn bleibt.
		assertThat(vertrag.verlaengerung()).isNull();
		assertThat(vertrag.kuendigungsfristMonate()).isNull();
		assertThat(vertrag.beginn()).isEqualTo(LocalDate.of(2020, 1, 1));
	}

	@Test
	void unbekannteKategorieUngueltig() {
		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(UUID.randomUUID()))))
				.isInstanceOfSatisfying(UngueltigeEingabeException.class, e -> {
					assertThat(e.getFeld()).isEqualTo("kategorieId");
					assertThat(e.getCode()).isEqualTo("Unbekannt");
				});
	}

	@Test
	void kategorieEinesAnderenHaushaltsUnbekannt() {
		UUID eigene = kategorie(haushalt);
		UUID fremde = kategorie(UUID.randomUUID());

		assertThat(HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(eigene))).kategorie().name())
				.isEqualTo("Eigene");
		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(fremde))))
				.isInstanceOf(UngueltigeEingabeException.class);
	}

	/** Eigene Kategorien kommen erst mit #34; bis dahin direkt in die Tabelle. */
	private UUID kategorie(UUID haushaltId) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO vertrag_kategorie (id, haushalt_id, name, reihenfolge) VALUES (?, ?, 'Eigene', 100)")
				.params(id, haushaltId)
				.update();
		return id;
	}

}
