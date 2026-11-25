package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragswerte;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preissteigerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration V11 (#156): Danach hat jeder Preis ein {@code gueltig_ab}, der früheste genau den Vertragsbeginn, und die
 * berechneten Werte bestehender Verträge sind dieselben wie vorher. Ohne Spring: eine eigene Datenbank im gemeinsamen
 * Container, Flyway bis V10, Verträge per SQL, dann V11.
 */
class PreisGueltigAbMigrationTests {

	private static final LocalDate HEUTE = LocalDate.of(2026, 6, 15);

	private static final Vertragsberechnung BERECHNUNG = new Vertragsberechnung(HEUTE);

	private static final UUID SONSTIGES = UUID.fromString("df419f96-28b5-4851-88c2-4634032072e6");

	private static final UUID NUR_OHNE_DATUM = UUID.randomUUID();

	private static final UUID OHNE_DATUM_UND_AM_BEGINN = UUID.randomUUID();

	private static final UUID OHNE_DATUM_UND_ANGEKUENDIGT = UUID.randomUUID();

	private static final UUID NUR_DATIERT_NACH_BEGINN = UUID.randomUUID();

	private static final UUID IN_ZUKUNFT_OHNE_DATUM = UUID.randomUUID();

	private static final UUID IN_ZUKUNFT_DATIERT_NACH_BEGINN = UUID.randomUUID();

	private static final UUID SCHON_AM_BEGINN = UUID.randomUUID();

	private static final UUID BEENDET = UUID.randomUUID();

	private static final Map<UUID, Vertragswerte> VORHER = new HashMap<>();

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrieren() {
		DataSource datenbank = TestcontainersConfiguration.neueDatenbank("migration");
		migrieren(datenbank, "10");
		jdbc = new JdbcTemplate(datenbank);
		vertrag(NUR_OHNE_DATUM, "2024-03-10", "MONATLICH", null, preis(null, "10.00"));
		jdbc.update("""
				UPDATE vertrag SET preissteigerung_art = 'PROZENT', preissteigerung_prozent = 5,
				    preissteigerung_rhythmus = 'JAEHRLICH', preissteigerung_naechste_am = '2027-01-01'
				WHERE id = ?""", NUR_OHNE_DATUM);
		vertrag(OHNE_DATUM_UND_AM_BEGINN, "2025-01-01", "MONATLICH", null,
				preis(null, "9.00"), preis("2025-01-01", "10.00"), preis("2026-04-01", "12.00"));
		vertrag(OHNE_DATUM_UND_ANGEKUENDIGT, "2025-02-15", "VIERTELJAEHRLICH", null,
				preis(null, "8.00"), preis("2026-08-01", "9.00"));
		vertrag(NUR_DATIERT_NACH_BEGINN, "2026-01-31", "MONATLICH", null,
				preis("2026-03-01", "20.00"), preis("2026-09-01", "22.00"));
		vertrag(IN_ZUKUNFT_OHNE_DATUM, "2026-09-01", "JAEHRLICH", null, preis(null, "15.00"));
		vertrag(IN_ZUKUNFT_DATIERT_NACH_BEGINN, "2026-10-01", "MONATLICH", null, preis("2026-12-01", "30.00"));
		vertrag(SCHON_AM_BEGINN, "2025-05-05", "MONATLICH", null,
				preis("2025-05-05", "5.00"), preis("2026-01-01", "6.00"));
		vertrag(BEENDET, "2023-01-01", "MONATLICH", "2025-12-31", preis(null, "7.00"), preis("2025-06-01", "8.00"));
		for (UUID id : alle()) {
			VORHER.put(id, werte(id));
		}
		migrieren(datenbank, "11");
	}

	@Test
	void jederPreisHatEinDatumDerFruehesteDenBeginn() {
		assertThat(preise(NUR_OHNE_DATUM)).containsExactly(preis("2024-03-10", "10.00"));
		// Der ohne Datum galt nur vor dem Beginn.
		assertThat(preise(OHNE_DATUM_UND_AM_BEGINN))
				.containsExactly(preis("2025-01-01", "10.00"), preis("2026-04-01", "12.00"));
		assertThat(preise(OHNE_DATUM_UND_ANGEKUENDIGT))
				.containsExactly(preis("2025-02-15", "8.00"), preis("2026-08-01", "9.00"));
		// Galt bis zum 01.03. rückwirkend.
		assertThat(preise(NUR_DATIERT_NACH_BEGINN))
				.containsExactly(preis("2026-01-31", "20.00"), preis("2026-09-01", "22.00"));
		assertThat(preise(IN_ZUKUNFT_OHNE_DATUM)).containsExactly(preis("2026-09-01", "15.00"));
		assertThat(preise(IN_ZUKUNFT_DATIERT_NACH_BEGINN)).containsExactly(preis("2026-10-01", "30.00"));
		assertThat(preise(SCHON_AM_BEGINN)).containsExactly(preis("2025-05-05", "5.00"), preis("2026-01-01", "6.00"));
		assertThat(preise(BEENDET)).containsExactly(preis("2023-01-01", "7.00"), preis("2025-06-01", "8.00"));
		assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns"
				+ " WHERE table_name = 'vertrag_preis' AND column_name = 'gueltig_ab'", String.class)).isEqualTo("NO");
	}

	/**
	 * Alles außer den Preiszeilen und dem Preisverlauf: Deren Daten sind jetzt ausgeschrieben, und ein Preis, der nur vor
	 * dem Beginn galt, fällt weg.
	 */
	@Test
	void berechneteWerteBleibenGleich() {
		for (UUID id : alle()) {
			assertThat(werte(id)).as(id.toString())
					.usingRecursiveComparison().ignoringFields("preise", "preisverlauf")
					.isEqualTo(VORHER.get(id));
		}
	}

	@Test
	void brichtBeiPreisVorDemBeginnAb() {
		DataSource datenbank = TestcontainersConfiguration.neueDatenbank("migration");
		migrieren(datenbank, "10");
		vertrag(new JdbcTemplate(datenbank), UUID.randomUUID(), "2025-01-01", "MONATLICH", null,
				preis("2024-12-01", "10.00"));

		assertThatThrownBy(() -> migrieren(datenbank, "11")).isInstanceOf(FlywayException.class)
				.hasMessageContaining("Preis vor dem Vertragsbeginn");
	}


	private static void migrieren(DataSource datenbank, String bis) {
		Flyway.configure().dataSource(datenbank).locations("classpath:db/migration").target(bis).load().migrate();
	}

	private static void vertrag(UUID id, String beginn, String zahlweise, String gekuendigtZum, Preis... preise) {
		vertrag(jdbc, id, beginn, zahlweise, gekuendigtZum, preise);
	}

	private static void vertrag(JdbcTemplate jdbc, UUID id, String beginn, String zahlweise, String gekuendigtZum, Preis... preise) {
		jdbc.update("""
				INSERT INTO vertrag (id, haushalt_id, version, angelegt_am, geaendert_am, anbieter, kategorie_id,
				    zahlweise, kuendbar, beginn, gekuendigt_zum, erinnerung_sonderkuendigung)
				VALUES (?, gen_random_uuid(), 0, now(), now(), 'Anbieter', ?, ?, true, ?, ?, true)""",
				id, SONSTIGES, zahlweise, Date.valueOf(beginn), gekuendigtZum == null ? null : Date.valueOf(gekuendigtZum));
		for (Preis preis : preise) {
			jdbc.update("INSERT INTO vertrag_preis (vertrag_id, gueltig_ab, betrag_wert, betrag_waehrung)"
					+ " VALUES (?, ?, ?, ?)", id, preis.gueltigAb() == null ? null : Date.valueOf(preis.gueltigAb()),
					preis.betrag().wert(), preis.betrag().waehrung().getCurrencyCode());
		}
	}

	private static Preis preis(String gueltigAb, String betrag) {
		return new Preis(gueltigAb == null ? null : LocalDate.parse(gueltigAb), euro(betrag));
	}

	private static List<UUID> alle() {
		return List.of(NUR_OHNE_DATUM, OHNE_DATUM_UND_AM_BEGINN, OHNE_DATUM_UND_ANGEKUENDIGT, NUR_DATIERT_NACH_BEGINN,
				IN_ZUKUNFT_OHNE_DATUM, IN_ZUKUNFT_DATIERT_NACH_BEGINN, SCHON_AM_BEGINN, BEENDET);
	}

	/** Sortiert; ein Preis ohne Datum (nur vor der Migration) zuerst. */
	private static List<Preis> preise(UUID vertrag) {
		return jdbc.query("SELECT gueltig_ab, betrag_wert, betrag_waehrung FROM vertrag_preis WHERE vertrag_id = ?"
						+ " ORDER BY gueltig_ab NULLS FIRST",
				(zeile, i) -> new Preis(zeile.getObject("gueltig_ab", LocalDate.class),
						new Betrag(zeile.getBigDecimal("betrag_wert"),
								Currency.getInstance(zeile.getString("betrag_waehrung")))),
				vertrag);
	}

	/**
	 * Die Werte wie im Backend berechnet. Vor der Migration gilt ein Preis ohne Datum wie früher vor allen anderen und
	 * rückwirkend: {@link LocalDate#MIN} ergibt genau das, weil die alte Berechnung das Datum des ersten Eintrags nie
	 * gelesen hat.
	 */
	private static Vertragswerte werte(UUID id) {
		var preise = preise(id).stream()
				.map(preis -> preis.gueltigAb() == null ? new Preis(LocalDate.MIN, preis.betrag()) : preis)
				.toList();
		return jdbc.queryForObject("SELECT * FROM vertrag WHERE id = ?", (zeile, i) -> {
			var daten = new Vertragsdaten(Zahlweise.valueOf(zeile.getString("zahlweise")), zeile.getBoolean("kuendbar"),
					zeile.getObject("beginn", LocalDate.class), zeile.getObject("laufzeitende", LocalDate.class),
					zeile.getString("verlaengerung") == null ? null : Verlaengerung.valueOf(zeile.getString("verlaengerung")),
					zeile.getObject("kuendigungsfrist_monate", Integer.class),
					zeile.getObject("gekuendigt_zum", LocalDate.class));
			var steigerung = zeile.getString("preissteigerung_art") == null ? null
					: new Preissteigerung(Steigerungsart.valueOf(zeile.getString("preissteigerung_art")),
							zeile.getBigDecimal("preissteigerung_prozent"), null,
							Erhoehungsrhythmus.valueOf(zeile.getString("preissteigerung_rhythmus")),
							zeile.getObject("preissteigerung_naechste_am", LocalDate.class));
			return BERECHNUNG.werte(new Berechnungsgrundlage(daten, new Preisliste(preise),
					new Preisprognose(steigerung, null), Anteil.aus((BigDecimal) null), List.of()));
		}, id);
	}

}
