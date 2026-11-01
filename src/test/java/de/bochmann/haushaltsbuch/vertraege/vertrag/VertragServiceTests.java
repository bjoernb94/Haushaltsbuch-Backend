package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.FachmodulTest;
import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
	private VertragRepository repository;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private Clock clock;

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

	@Test
	void listetNachDringlichkeitUndName() {
		LocalDate heute = LocalDate.now(clock);
		HaushaltKontext.fuer(haushalt, () -> {
			anlegen("Musterversicherung AG", "Hausratversicherung", Zahlweise.JAEHRLICH, true, heute.plusDays(300),
					Verlaengerung.ZWOELF_MONATE, 1, null);
			anlegen("Mobilfunk GmbH", "Handyvertrag", Zahlweise.MONATLICH, true, heute.plusDays(40), Verlaengerung.KEINE,
					null, null);
			anlegen("Musterversicherung AG", "Kfz-Versicherung", Zahlweise.JAEHRLICH, true, heute.plusDays(60),
					Verlaengerung.ZWOELF_MONATE, 1, null);
			anlegen("Streamingdienst", null, Zahlweise.MONATLICH, true, null, Verlaengerung.ZAHLUNGSINTERVALL, 0, null);
			anlegen("Beitragsservice", "Rundfunkbeitrag", Zahlweise.VIERTELJAEHRLICH, false, null, null, null, null);
			anlegen("Musterverlag", "Zeitschriften-Abo", Zahlweise.MONATLICH, true, null, Verlaengerung.MONATLICH, 1,
					heute.plusDays(20));
			anlegen("Altanbieter", null, Zahlweise.MONATLICH, true, null, Verlaengerung.MONATLICH, 1, heute.minusDays(1));
			// Ohne Vertragsende, gleich dringend: nach dem angezeigten Namen, ohne Groß-/Kleinschreibung.
			anlegen("verein b", null, Zahlweise.JAEHRLICH, true, null, Verlaengerung.KEINE, null, null);
			anlegen("Zeta GmbH", "Ärzteverband", Zahlweise.JAEHRLICH, true, null, Verlaengerung.KEINE, null, null);
			return null;
		});

		var liste = HaushaltKontext.fuer(haushalt, service::auflisten);

		assertThat(liste).extracting(v -> v.bezeichnung() != null ? v.bezeichnung() : v.anbieter()).containsExactly(
				"Kfz-Versicherung", "Handyvertrag", "Hausratversicherung", "Streamingdienst", "Ärzteverband", "verein b",
				"Rundfunkbeitrag", "Zeitschriften-Abo", "Altanbieter");
		assertThat(liste).extracting(v -> v.berechnet().kuendigung().art()).containsExactly(Kuendigungsart.KUENDIGEN_BIS,
				Kuendigungsart.ENDET, Kuendigungsart.KUENDIGEN_BIS, Kuendigungsart.MONATLICH_KUENDBAR,
				Kuendigungsart.KEINE_LAUFZEIT, Kuendigungsart.KEINE_LAUFZEIT, Kuendigungsart.NICHT_KUENDBAR,
				Kuendigungsart.GEKUENDIGT, Kuendigungsart.BEENDET);
		assertThat(liste.getFirst().kategorie().schluessel()).isEqualTo("VERSICHERUNG");
	}

	@Test
	void listeOhneVertraegeLeer() {
		assertThat(HaushaltKontext.fuer(haushalt, service::auflisten)).isEmpty();
	}

	@Test
	void listetNurVertraegeDesAktuellenHaushalts() {
		var eigener = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));
		HaushaltKontext.fuer(UUID.randomUUID(), () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThat(HaushaltKontext.fuer(haushalt, service::auflisten)).extracting(VertragAntwort::id)
				.containsExactly(eigener.id());
	}

	@Test
	void laedtKategorieMitDerListe() {
		HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		var vertraege = HaushaltKontext.fuer(haushalt, () -> repository.findAll());

		assertThat(vertraege).singleElement().satisfies(v -> assertThat(Hibernate.isInitialized(v.getKategorie())).isTrue());
	}

	@Test
	void uebersichtZaehltLaufendeUndSummiertKosten() {
		LocalDate heute = LocalDate.now(clock);
		HaushaltKontext.fuer(haushalt, () -> {
			anlegen("Musterversicherung AG", "Kfz-Versicherung", Zahlweise.JAEHRLICH, true, heute.plusDays(60),
					Verlaengerung.ZWOELF_MONATE, 1, null);
			anlegen("Verein", null, Zahlweise.JAEHRLICH, true, null, Verlaengerung.KEINE, null, null);
			anlegen("Musterverlag", "Zeitschriften-Abo", Zahlweise.MONATLICH, true, null, Verlaengerung.MONATLICH, 1,
					heute.plusDays(20));
			anlegen("Altanbieter", null, Zahlweise.MONATLICH, true, null, Verlaengerung.MONATLICH, 1, heute.minusDays(1));
			return null;
		});

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		// Der beendete zählt nicht, der gekündigte bis zu seinem Ende schon.
		assertThat(uebersicht.laufendeVertraege()).isEqualTo(3);
		assertThat(uebersicht.kostenProJahr()).containsExactly(euro("140.00"));
		// Gerundet erst die Summe: 140 / 12 = 11,67, nicht 0,83 + 0,83 + 10,00 = 11,66.
		assertThat(uebersicht.kostenProMonat()).containsExactly(euro("11.67"));
	}

	@Test
	void uebersichtNenntDieNaechstenKuendigungsfristen() {
		LocalDate heute = LocalDate.now(clock);
		HaushaltKontext.fuer(haushalt, () -> {
			for (int i = 7; i >= 1; i--) {
				anlegen("Anbieter " + i, null, Zahlweise.JAEHRLICH, true, heute.plusDays(40 + 30L * i),
						Verlaengerung.ZWOELF_MONATE, 1, null);
			}
			// Endet ohne Kündigung oder ist jederzeit kündbar: keine Frist für die Kachel.
			anlegen("Mobilfunk GmbH", null, Zahlweise.MONATLICH, true, heute.plusDays(5), Verlaengerung.KEINE, null,
					null);
			anlegen("Streamingdienst", null, Zahlweise.MONATLICH, true, null, Verlaengerung.ZAHLUNGSINTERVALL, 0, null);
			return null;
		});

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);
		var liste = HaushaltKontext.fuer(haushalt, service::auflisten);

		assertThat(uebersicht.laufendeVertraege()).isEqualTo(9);
		assertThat(uebersicht.naechsteKuendigungsfristen())
				.extracting(VertragsuebersichtAntwort.AnstehendeKuendigung::anbieter)
				.containsExactly("Anbieter 1", "Anbieter 2", "Anbieter 3", "Anbieter 4", "Anbieter 5");
		var erster = liste.stream().filter(v -> v.anbieter().equals("Anbieter 1")).findFirst().orElseThrow();
		assertThat(uebersicht.naechsteKuendigungsfristen().getFirst()).isEqualTo(
				new VertragsuebersichtAntwort.AnstehendeKuendigung(erster.id(), "Anbieter 1", null, euro("10.00"),
						Zahlweise.JAEHRLICH, erster.berechnet().kuendigung().kuendigenBis(), heute.plusDays(70),
						erster.berechnet().kuendigung().dringend()));
	}

	@Test
	void uebersichtOhneVertraegeLeer() {
		assertThat(HaushaltKontext.fuer(haushalt, service::uebersicht))
				.isEqualTo(new VertragsuebersichtAntwort(0, List.of(), List.of(), List.of()));
	}

	/** Die Angaben von {@code anfrage} als Änderung mit {@code version}. */
	static VertragAenderung aenderung(long version, VertragAnfrage a) {
		return new VertragAenderung(version, a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				a.betrag(), a.zahlweise(), a.abbuchungAb(), a.zahlungsweg(), a.kuendbar(), a.beginn(), a.laufzeitende(),
				a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	/** Wie {@link #anfrage}, aber monatlich 50 € statt jährlich 586,80 €. */
	private static VertragAnfrage monatlich() {
		var a = anfrage(VERSICHERUNG);
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(), euro("50.00"),
				Zahlweise.MONATLICH, LocalDate.of(2026, 12, 1), a.zahlungsweg(), a.kuendbar(), a.beginn(), a.laufzeitende(),
				a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	@Test
	void aendertVertragUndLiefertNeueVersion() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		var geaendert = HaushaltKontext.fuer(haushalt,
				() -> service.aendern(angelegt.id(), aenderung(angelegt.version(), monatlich())));

		assertThat(geaendert.version()).isEqualTo(angelegt.version() + 1);
		assertThat(geaendert.zahlweise()).isEqualTo(Zahlweise.MONATLICH);
		assertThat(geaendert.abbuchungAb()).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(geaendert.berechnet().kostenProJahr()).isEqualTo(euro("600.00"));
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(angelegt.id()))).isEqualTo(geaendert);
	}

	@Test
	void veralteteVersionKonflikt() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));
		HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0, monatlich())));

		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt,
				() -> service.aendern(angelegt.id(), aenderung(0, anfrage(VERSICHERUNG)))))
				.isInstanceOf(KonfliktException.class);
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(angelegt.id())).zahlweise())
				.isEqualTo(Zahlweise.MONATLICH);
	}

	@Test
	void aenderungOhneUnterschiedBehaeltVersion() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		var geaendert = HaushaltKontext.fuer(haushalt,
				() -> service.aendern(angelegt.id(), aenderung(0, anfrage(VERSICHERUNG))));

		assertThat(geaendert.version()).isZero();
	}

	@Test
	void nichtKuendbarLeertVerlaengerungUndFrist() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));
		var a = anfrage(VERSICHERUNG);
		var nichtKuendbar = new VertragAenderung(0L, a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				a.betrag(), a.zahlweise(), a.abbuchungAb(), a.zahlungsweg(), false, a.beginn(), a.laufzeitende(),
				a.verlaengerung(), a.kuendigungsfristMonate(), null, null);

		var geaendert = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), nichtKuendbar));

		assertThat(geaendert.verlaengerung()).isNull();
		assertThat(geaendert.kuendigungsfristMonate()).isNull();
		assertThat(geaendert.laufzeitende()).isEqualTo(a.laufzeitende());
		assertThat(geaendert.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.NICHT_KUENDBAR);
	}

	@Test
	void vertragEinesAnderenHaushaltsNichtGefunden() {
		var fremder = HaushaltKontext.fuer(UUID.randomUUID(), () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt,
				() -> service.aendern(fremder.id(), aenderung(0, anfrage(VERSICHERUNG)))))
				.isInstanceOf(NichtGefundenException.class);
	}

	@Test
	void loeschtVertrag() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		HaushaltKontext.fuer(haushalt, () -> {
			service.loeschen(angelegt.id(), angelegt.version());
			return null;
		});

		assertThat(HaushaltKontext.fuer(haushalt, service::auflisten)).isEmpty();
		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt, () -> {
			service.loeschen(angelegt.id(), angelegt.version());
			return null;
		})).isInstanceOf(NichtGefundenException.class);
	}

	@Test
	void loeschenMitVeralteterVersionKonflikt() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));
		HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0, monatlich())));

		assertThatThrownBy(() -> HaushaltKontext.fuer(haushalt, () -> {
			service.loeschen(angelegt.id(), 0);
			return null;
		})).isInstanceOf(KonfliktException.class);
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(angelegt.id())).version()).isEqualTo(1);
	}

	private void anlegen(String anbieter, String bezeichnung, Zahlweise zahlweise, boolean kuendbar,
			LocalDate laufzeitende, Verlaengerung verlaengerung, Integer frist, LocalDate gekuendigtZum) {
		service.anlegen(new VertragAnfrage(anbieter, bezeichnung, VERSICHERUNG, null, euro("10.00"), zahlweise,
				LocalDate.now(clock).plusDays(10), null, kuendbar, null, laufzeitende, verlaengerung, frist, gekuendigtZum,
				null));
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
