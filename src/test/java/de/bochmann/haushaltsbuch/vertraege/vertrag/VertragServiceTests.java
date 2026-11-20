package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.FachmodulTest;
import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisaenderungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preispunkt;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preispunktart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preiszeilenart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Prognosepreis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Steigerungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieSchluessel;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
		return new VertragAnfrage("Musterversicherung AG", "Kfz-Versicherung", kategorieId, "KFZ-4711",
				preis(LocalDate.of(2023, 1, 1), "586.80"),
				Zahlweise.JAEHRLICH, Zahlungsweg.LASTSCHRIFT, null, List.of(), null, null, true, true, LocalDate.of(2023, 1, 1),
				LocalDate.of(2023, 12, 31), Verlaengerung.ZWOELF_MONATE, 1, null, "Notiz");
	}

	/** Ein einziger Preis, gültig ab {@code beginn} (dem Vertragsbeginn). */
	static List<VertragPreisAnfrage> preis(LocalDate beginn, String wert) {
		return List.of(new VertragPreisAnfrage(beginn, euro(wert)));
	}

	@Test
	void legtVertragImAktuellenHaushaltAn() {
		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThat(jdbc.sql("SELECT haushalt_id FROM vertrag WHERE id = ?").param(vertrag.id()).query(UUID.class).single())
				.isEqualTo(haushalt);
		assertThat(vertrag.kategorie().schluessel()).isEqualTo(VertragKategorieSchluessel.VERSICHERUNG);
		assertThat(vertrag.version()).isZero();
		assertThat(vertrag.berechnet().kostenProMonat()).isEqualTo(euro("48.90"));
	}

	@Test
	void leertUnpassendeAngaben() {
		var rundfunk = new VertragAnfrage("  Beitragsservice  ", " ", VERSICHERUNG, "", preis(LocalDate.of(2020, 1, 1), "55.08"),
				Zahlweise.VIERTELJAEHRLICH, null, null, List.of(), null, null, true, false, LocalDate.of(2020, 1, 1), null,
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
		assertThat(liste.getFirst().kategorie().schluessel()).isEqualTo(VertragKategorieSchluessel.VERSICHERUNG);
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
		// Gerundet erst die Summe: 140 / 12 = 11,67, nicht 0,83 + 0,83 + 10,00 = 11,66.
		assertThat(uebersicht.kostenProMonat()).containsExactly(euro("11.67"));
	}

	/**
	 * Kosten im Jahr über laufende und im Jahr beendete Verträge; je mit festem Beginn, damit die Zahl der
	 * Abbuchungen nicht vom Testtag abhängt.
	 */
	@Test
	void uebersichtKostenImJahrMitImJahrBeendeten() {
		int jahr = LocalDate.now(clock).getYear();
		LocalDate januar = LocalDate.of(jahr, 1, 1);
		HaushaltKontext.fuer(haushalt, () -> {
			// Zwölfmal 10 €.
			service.anlegen(mit(anfrage(VERSICHERUNG), preis(januar, "10.00"), Zahlweise.MONATLICH, januar, null));
			// Einmal 100 €, gekündigt zum 01.01.: ab dem 02.01. beendet, hat aber im Jahr gekostet.
			service.anlegen(mit(anfrage(VERSICHERUNG), preis(januar, "100.00"), Zahlweise.JAEHRLICH, januar, januar));
			// Im Vorjahr beendet: zählt nicht.
			service.anlegen(mit(anfrage(VERSICHERUNG), preis(januar.minusMonths(1), "1000.00"),
					Zahlweise.MONATLICH, januar.minusMonths(1),
					januar.minusDays(1)));
			return null;
		});

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(uebersicht.kostenImJahr()).containsExactly(euro("220.00"));
		assertThat(uebersicht.jahr()).isEqualTo(jahr);
	}

	/** Monatlich 10 € ab {@code beginn}, Verlängerung um zwölf Monate ab Beginn mit einem Monat Frist. */
	private static VertragAnfrage ab(String anbieter, LocalDate beginn, LocalDate gekuendigtZum) {
		return new VertragAnfrage(anbieter, null, VERSICHERUNG, null, preis(beginn, "10.00"), Zahlweise.MONATLICH, null, null,
				List.of(), null, null, true, true, beginn, null, Verlaengerung.ZWOELF_MONATE, 1, gekuendigtZum, null);
	}

	/**
	 * Noch nicht begonnene zählen nicht als laufend und nicht pro Monat, aber mit ihren Abbuchungen im Jahr und ihrer
	 * Kündigungsfrist; schon gekündigte stehen in der Liste bei den gekündigten; stornierte zählen gar nicht (#110).
	 */
	@Test
	void uebersichtMitNichtBegonnenenUndStornierten() {
		LocalDate heute = LocalDate.now(clock);
		LocalDate januar = LocalDate.of(heute.getYear(), 1, 1);
		var vertraege = HaushaltKontext.fuer(haushalt, () -> List.of(
				service.anlegen(ab("Laufend", januar, null)),
				service.anlegen(ab("Später", heute.plusDays(1), null)),
				service.anlegen(ab("Später gekündigt", heute.plusDays(1), heute.plusDays(100))),
				service.anlegen(ab("Storniert", heute.plusDays(10), heute.plusDays(9)))));
		var spaeter = vertraege.get(1);
		var spaeterGekuendigt = vertraege.get(2);
		var storniert = vertraege.get(3);

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(spaeter.berechnet().status()).isEqualTo(Vertragsstatus.NICHT_BEGONNEN);
		assertThat(spaeter.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.KUENDIGEN_BIS);
		assertThat(spaeterGekuendigt.berechnet().status()).isEqualTo(Vertragsstatus.NICHT_BEGONNEN);
		assertThat(spaeterGekuendigt.berechnet().kuendigung()).isEqualTo(
				new Kuendigung(Kuendigungsart.GEKUENDIGT, null, heute.plusDays(100), 1, false));
		assertThat(storniert.berechnet().status()).isEqualTo(Vertragsstatus.STORNIERT);
		assertThat(storniert.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.STORNIERT);
		assertThat(storniert.berechnet().kostenImJahr()).isEqualTo(euro("0.00"));
		assertThat(storniert.berechnet().vertragsende()).isEqualTo(heute.plusDays(9));
		assertThat(uebersicht.laufendeVertraege()).isEqualTo(1);
		assertThat(uebersicht.kostenProMonat()).containsExactly(euro("10.00"));
		assertThat(uebersicht.hatNichtBegonnene()).isTrue();
		assertThat(uebersicht.kostenImJahr()).containsExactly(euro("120.00").plus(spaeter.berechnet().kostenImJahr())
				.plus(spaeterGekuendigt.berechnet().kostenImJahr()));
		assertThat(uebersicht.naechsteKuendigungsfristen())
				.extracting(VertragsuebersichtAntwort.AnstehendeKuendigung::anbieter)
				.containsExactlyInAnyOrder("Laufend", "Später");
		// Der schon gekündigte steht bei den gekündigten (nach den Fristen), stornierte wie beendete am Ende.
		assertThat(HaushaltKontext.fuer(haushalt, service::auflisten)).extracting(VertragAntwort::anbieter)
				.endsWith("Später gekündigt", "Storniert");
	}

	/** Mehrere Preise: sortiert, der heute gültige ist der Betrag, der nächste die angekündigte Änderung. */
	@Test
	void rechnetMitDemJeweilsGueltigenPreis() {
		LocalDate heute = LocalDate.now(clock);
		var preise = List.of(new VertragPreisAnfrage(heute.plusDays(30), euro("12.00")),
				new VertragPreisAnfrage(heute.minusYears(1), euro("10.00")),
				new VertragPreisAnfrage(heute.minusDays(100), euro("11.00")));

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mit(anfrage(VERSICHERUNG), preise,
				Zahlweise.MONATLICH, heute.minusYears(1), null)));

		assertThat(vertrag.preise()).extracting(VertragPreisAntwort::gueltigAb, VertragPreisAntwort::art)
				.containsExactly(tuple(heute.minusYears(1), Preiszeilenart.FRUEHER), tuple(heute.minusDays(100), Preiszeilenart.AKTUELL),
						tuple(heute.plusDays(30), Preiszeilenart.ANGEKUENDIGT));
		assertThat(vertrag.betrag()).isEqualTo(euro("11.00"));
		assertThat(vertrag.berechnet().kostenProMonat()).isEqualTo(euro("11.00"));
		assertThat(vertrag.berechnet().naechstePreisaenderung())
				.isEqualTo(new Preisaenderung(heute.plusDays(30), euro("12.00"), Preisaenderungsart.ANGEKUENDIGT));
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(vertrag.id()))).isEqualTo(vertrag);
	}

	@Test
	void vertragInDerZukunftHatDenErstenPreis() {
		LocalDate heute = LocalDate.now(clock);
		var preise = List.of(new VertragPreisAnfrage(heute.plusMonths(2), euro("20.00")),
				new VertragPreisAnfrage(heute.plusMonths(1), euro("15.00")));

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mit(anfrage(VERSICHERUNG), preise,
				Zahlweise.MONATLICH, heute.plusMonths(1), null)));

		assertThat(vertrag.berechnet().status()).isEqualTo(Vertragsstatus.NICHT_BEGONNEN);
		assertThat(vertrag.betrag()).isEqualTo(euro("15.00"));
		assertThat(vertrag.preise()).extracting(VertragPreisAntwort::art)
				.containsExactly(Preiszeilenart.AKTUELL, Preiszeilenart.ANGEKUENDIGT);
		assertThat(vertrag.berechnet().naechstePreisaenderung().ab()).isEqualTo(heute.plusMonths(2));
	}

	/** Ein Preis nach dem Vertragsende wurde nie gezahlt: Betrag ist der am letzten Tag gültige (#104). */
	@Test
	void beendeterVertragHatDenPreisAmLetztenTag() {
		LocalDate heute = LocalDate.now(clock);
		var preise = List.of(new VertragPreisAnfrage(heute.minusMonths(3), euro("10.00")),
				new VertragPreisAnfrage(heute.minusDays(10), euro("12.00")));

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mit(anfrage(VERSICHERUNG), preise,
				Zahlweise.MONATLICH, heute.minusMonths(3), heute.minusDays(20))));

		assertThat(vertrag.berechnet().status()).isEqualTo(Vertragsstatus.BEENDET);
		assertThat(vertrag.betrag()).isEqualTo(euro("10.00"));
		assertThat(vertrag.berechnet().kostenProMonat()).isEqualTo(euro("10.00"));
		assertThat(vertrag.preise()).extracting(VertragPreisAntwort::art)
				.containsExactly(Preiszeilenart.AKTUELL, Preiszeilenart.NACH_ENDE);
	}

	/** Dieselben Preise in anderer Reihenfolge sind keine Änderung. */
	@Test
	void preiseInAndererReihenfolgeBehaltenVersion() {
		LocalDate heute = LocalDate.now(clock);
		var a = new VertragPreisAnfrage(heute.minusYears(1), euro("10.00"));
		var b = new VertragPreisAnfrage(heute.plusDays(30), euro("12.00"));
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mit(anfrage(VERSICHERUNG), List.of(a, b),
				Zahlweise.MONATLICH, heute.minusYears(1), null)));

		var gleich = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0,
				mit(anfrage(VERSICHERUNG), List.of(b, a), Zahlweise.MONATLICH, heute.minusYears(1), null))));
		var weniger = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0,
				mit(anfrage(VERSICHERUNG), List.of(a), Zahlweise.MONATLICH, heute.minusYears(1), null))));

		assertThat(gleich.version()).isZero();
		assertThat(weniger.version()).isEqualTo(1);
		assertThat(weniger.preise())
				.containsExactly(new VertragPreisAntwort(heute.minusYears(1), euro("10.00"), Preiszeilenart.AKTUELL));
		assertThat(weniger.berechnet().naechstePreisaenderung()).isNull();
	}

	/** {@code a} mit anderen Preisen, Zahlweise, Beginn und Kündigung. */
	private static VertragAnfrage mit(VertragAnfrage a, List<VertragPreisAnfrage> preise, Zahlweise zahlweise,
			LocalDate beginn, LocalDate gekuendigtZum) {
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(), preise, zahlweise,
				a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(), beginn, a.laufzeitende(), a.verlaengerung(),
				a.kuendigungsfristMonate(), gekuendigtZum, a.notiz());
	}

	/** {@code a} mit eigenem Anteil in Prozent. */
	private static VertragAnfrage mitAnteil(VertragAnfrage a, String prozent) {
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(), a.preise(),
				a.zahlweise(), a.zahlungsweg(), prozent != null ? new BigDecimal(prozent) : null,
				a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(), a.beginn(), a.laufzeitende(), a.verlaengerung(), a.kuendigungsfristMonate(),
				a.gekuendigtZum(), a.notiz());
	}

	/** Monatlich {@code wert} ab Jahresbeginn: zwölf Abbuchungen, unabhängig vom Testtag. */
	private VertragAnfrage monatlichImJahr(String wert) {
		LocalDate januar = LocalDate.of(LocalDate.now(clock).getYear(), 1, 1);
		return mit(anfrage(VERSICHERUNG), preis(januar, wert), Zahlweise.MONATLICH, januar, null);
	}

	@Test
	void eigenerAnteilMit50Und33Komma3Prozent() {
		var haelfte = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(monatlichImJahr("10.00"), "50")));
		var drittel = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(monatlichImJahr("10.00"), "33.3")));

		assertThat(haelfte.anteilProzent()).isEqualTo(new BigDecimal("50.0"));
		assertThat(haelfte.berechnet().eigenerAnteil())
				.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("5.00"), euro("5.00"), euro("60.00")));
		// 10 € × 33,3 % = 3,33 € je Abbuchung; 120 € × 33,3 % = 39,96 € im Jahr, ÷ 12 = 3,33 € im Monat.
		assertThat(drittel.berechnet().eigenerAnteil())
				.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("3.33"), euro("3.33"), euro("39.96")));
		// Abgebucht wird weiter der volle Betrag.
		assertThat(drittel.betrag()).isEqualTo(euro("10.00"));
		assertThat(drittel.berechnet().kostenProMonat()).isEqualTo(euro("10.00"));
		assertThat(drittel.berechnet().kostenImJahr()).isEqualTo(euro("120.00"));
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(drittel.id()))).isEqualTo(drittel);
	}

	/**
	 * Jährlich 586,80 € (Artboard „Dein Anteil“): 50 % = 293,40 € je Abbuchung; 33,3 % = 195,40 € (195,4044), im
	 * Monat 16,28 € (16,2837), jeder Wert aus dem ungerundeten gerundet (ADR 0011).
	 */
	@Test
	void eigenerAnteilAmBetragJaehrlich() {
		LocalDate beginn = LocalDate.of(LocalDate.now(clock).getYear() - 1, 6, 1);
		var jaehrlich = mit(anfrage(VERSICHERUNG), preis(beginn, "586.80"), Zahlweise.JAEHRLICH, beginn, null);

		var haelfte = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(jaehrlich, "50")));
		var drittel = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(jaehrlich, "33.3")));

		assertThat(haelfte.berechnet().eigenerAnteil())
				.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("293.40"), euro("24.45"), euro("293.40")));
		assertThat(drittel.berechnet().eigenerAnteil())
				.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("195.40"), euro("16.28"), euro("195.40")));
		assertThat(drittel.betrag()).isEqualTo(euro("586.80"));
	}

	@Test
	void ohneAnteilUnter100KeinEigenerAnteil() {
		var ohne = HaushaltKontext.fuer(haushalt, () -> service.anlegen(monatlichImJahr("10.00")));
		var ganz = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(monatlichImJahr("10.00"), "100")));

		assertThat(ohne.anteilProzent()).isNull();
		assertThat(ohne.berechnet().eigenerAnteil()).isNull();
		assertThat(ganz.anteilProzent()).isEqualTo(new BigDecimal("100.0"));
		assertThat(ganz.berechnet().eigenerAnteil()).isNull();
		assertThat(HaushaltKontext.fuer(haushalt, service::uebersicht).hatAnteile()).isFalse();
	}

	@Test
	void anteilAendernUndGleichenAnteilBehaeltVersion() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(anfrage(VERSICHERUNG), "50")));

		// 50 und 50.0 sind derselbe Anteil.
		var gleich = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(),
				aenderung(0, mitAnteil(anfrage(VERSICHERUNG), "50.0"))));
		var geaendert = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(),
				aenderung(0, mitAnteil(anfrage(VERSICHERUNG), "33.3"))));
		var entfernt = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(),
				aenderung(1, anfrage(VERSICHERUNG))));

		assertThat(gleich.version()).isZero();
		assertThat(geaendert.version()).isEqualTo(1);
		assertThat(geaendert.anteilProzent()).isEqualTo(new BigDecimal("33.3"));
		assertThat(entfernt.anteilProzent()).isNull();
		assertThat(entfernt.berechnet().eigenerAnteil()).isNull();
	}

	/**
	 * Die Übersicht wendet den Anteil je Vertrag ungerundet an und rundet erst die Summe (ADR 0011). Zweimal jährlich
	 * 10,01 € zu 50 %: im Jahr 5,005 + 5,005 = 10,01 € (einzeln gerundet 10,02 €), im Monat 10,01 ÷ 12 = 0,83 €
	 * (einzeln gerundet 0,42 + 0,42 = 0,84 €). Dazu ein Vertrag ohne Anteil, der voll zählt.
	 */
	@Test
	void uebersichtZaehltNurDenAnteilUndRundetDieSumme() {
		int jahr = LocalDate.now(clock).getYear();
		var geteilt = mitAnteil(mit(anfrage(VERSICHERUNG), preis(LocalDate.of(jahr, 1, 1), "10.01"),
				Zahlweise.JAEHRLICH, LocalDate.of(jahr, 1, 1),
				null), "50");
		HaushaltKontext.fuer(haushalt, () -> {
			var erster = service.anlegen(geteilt);
			service.anlegen(geteilt);
			assertThat(erster.berechnet().eigenerAnteil())
					.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("5.01"), euro("0.42"), euro("5.01")));
			return null;
		});

		var nurGeteilte = HaushaltKontext.fuer(haushalt, service::uebersicht);
		HaushaltKontext.fuer(haushalt, () -> service.anlegen(monatlichImJahr("12.00")));
		var mitVollem = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(nurGeteilte.kostenImJahr()).containsExactly(euro("10.01"));
		assertThat(nurGeteilte.kostenProMonat()).containsExactly(euro("0.83"));
		assertThat(nurGeteilte.hatAnteile()).isTrue();
		// 10,01 + 144,00 im Jahr; im Monat 154,01 ÷ 12 = 12,834 → 12,83.
		assertThat(mitVollem.kostenImJahr()).containsExactly(euro("154.01"));
		assertThat(mitVollem.kostenProMonat()).containsExactly(euro("12.83"));
	}

	/** Ein beendeter geteilter Vertrag, der im Jahr nichts mehr gekostet hat, zählt nicht und setzt kein Kennzeichen. */
	@Test
	void uebersichtOhneAnteilVonVorjahrBeendetem() {
		LocalDate januar = LocalDate.of(LocalDate.now(clock).getYear(), 1, 1);
		HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitAnteil(mit(anfrage(VERSICHERUNG),
				preis(januar.minusMonths(1), "10.00"), Zahlweise.MONATLICH, januar.minusMonths(1), januar.minusDays(1)), "50")));

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(uebersicht.kostenImJahr()).isEmpty();
		assertThat(uebersicht.hatAnteile()).isFalse();
	}

	/** {@code a} mit den Einmalkosten {@code eintraege}. */
	private static VertragAnfrage mitEinmalkosten(VertragAnfrage a, VertragEinmalkostenAnfrage... eintraege) {
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(), a.preise(),
				a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), List.of(eintraege), a.preissteigerung(),
				a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(),
				a.beginn(), a.laufzeitende(), a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	private static VertragEinmalkostenAnfrage einmal(String bezeichnung, String wert, LocalDate faelligAm) {
		return new VertragEinmalkostenAnfrage(bezeichnung, euro(wert), faelligAm);
	}

	/** Sortiert nach Fälligkeit und Bezeichnung, gleiche Einträge bleiben; die Summe zählt alle, voll. */
	@Test
	void einmalkostenSortiertUndSummiert() {
		int jahr = LocalDate.now(clock).getYear();
		var router = einmal("Router", "99.99", LocalDate.of(jahr, 3, 1));
		var anschluss = einmal("  Anschluss ", "49.00", LocalDate.of(jahr - 1, 3, 1));
		var anfrage = mitEinmalkosten(mitAnteil(monatlichImJahr("10.00"), "50"), router, anschluss, router);

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage));

		assertThat(vertrag.einmalkosten()).containsExactly(
				new VertragEinmalkostenAntwort("Anschluss", euro("49.00"), LocalDate.of(jahr - 1, 3, 1)),
				new VertragEinmalkostenAntwort("Router", euro("99.99"), LocalDate.of(jahr, 3, 1)),
				new VertragEinmalkostenAntwort("Router", euro("99.99"), LocalDate.of(jahr, 3, 1)));
		assertThat(vertrag.berechnet().einmalkosten()).isEqualTo(euro("248.98"));
		// Laufende Kosten und eigener Anteil ohne Einmalkosten.
		assertThat(vertrag.berechnet().kostenImJahr()).isEqualTo(euro("120.00"));
		assertThat(vertrag.berechnet().eigenerAnteil())
				.isEqualTo(new VertragAntwort.VertragEigenerAnteil(euro("5.00"), euro("5.00"), euro("60.00")));
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(vertrag.id()))).isEqualTo(vertrag);
	}

	@Test
	void ohneEinmalkostenSummeNullInDerWaehrungDerPreise() {
		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThat(vertrag.einmalkosten()).isEmpty();
		assertThat(vertrag.berechnet().einmalkosten()).isEqualTo(euro("0.00"));
	}

	/** Andere Reihenfolge ist keine Änderung; Entfernen schon, und der Vertrag lässt sich mit Einträgen löschen. */
	@Test
	void einmalkostenAendernUndLoeschen() {
		LocalDate heute = LocalDate.now(clock);
		var a = einmal("Router", "99.99", heute);
		var b = einmal("Anschluss", "49.00", heute.minusDays(1));
		var angelegt = HaushaltKontext.fuer(haushalt,
				() -> service.anlegen(mitEinmalkosten(anfrage(VERSICHERUNG), a, b)));

		var gleich = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(),
				aenderung(0, mitEinmalkosten(anfrage(VERSICHERUNG), b, a))));
		var weniger = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(),
				aenderung(0, mitEinmalkosten(anfrage(VERSICHERUNG), a))));

		assertThat(gleich.version()).isZero();
		assertThat(weniger.version()).isEqualTo(1);
		assertThat(weniger.einmalkosten()).extracting(VertragEinmalkostenAntwort::bezeichnung).containsExactly("Router");
		assertThat(weniger.berechnet().einmalkosten()).isEqualTo(euro("99.99"));
		HaushaltKontext.fuer(haushalt, () -> {
			service.loeschen(angelegt.id(), 1);
			return null;
		});
		assertThat(jdbc.sql("SELECT count(*) FROM vertrag_einmalkosten WHERE vertrag_id = ?").param(angelegt.id())
				.query(Long.class).single()).isZero();
	}

	/**
	 * Einmalkosten im Jahr über alle Verträge, je mit dem Anteil ungerundet, erst die Summe gerundet (ADR 0011):
	 * zweimal 10,01 € zu 50 % sind 10,01 € (einzeln gerundet 10,02 €). Einträge im Vorjahr zählen nicht.
	 */
	@Test
	void uebersichtEinmalkostenImJahrMitAnteil() {
		int jahr = LocalDate.now(clock).getYear();
		var geteilt = mitEinmalkosten(mitAnteil(monatlichImJahr("10.00"), "50"),
				einmal("Router", "10.01", LocalDate.of(jahr, 3, 1)), einmal("Alt", "99.00", LocalDate.of(jahr - 1, 3, 1)));
		HaushaltKontext.fuer(haushalt, () -> {
			service.anlegen(geteilt);
			service.anlegen(geteilt);
			return null;
		});

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(uebersicht.einmalkostenImJahr()).containsExactly(euro("10.01"));
		// Laufende Kosten ohne Einmalkosten: zweimal 120 € zur Hälfte.
		assertThat(uebersicht.kostenImJahr()).containsExactly(euro("120.00"));
	}

	/** Ein im Vorjahr beendeter geteilter Vertrag zählt mit seinen Einmalkosten im Jahr und setzt {@code hatAnteile}. */
	@Test
	void uebersichtEinmalkostenAuchVonBeendeten() {
		LocalDate januar = LocalDate.of(LocalDate.now(clock).getYear(), 1, 1);
		HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitEinmalkosten(mitAnteil(mit(anfrage(VERSICHERUNG),
				preis(januar.minusMonths(1), "10.00"), Zahlweise.MONATLICH, januar.minusMonths(1), januar.minusDays(1)),
				"33.3"),
				einmal("Rücksendung Router", "30.00", januar.plusDays(10)))));

		var uebersicht = HaushaltKontext.fuer(haushalt, service::uebersicht);

		assertThat(uebersicht.laufendeVertraege()).isZero();
		assertThat(uebersicht.kostenImJahr()).isEmpty();
		assertThat(uebersicht.einmalkostenImJahr()).containsExactly(euro("9.99"));
		assertThat(uebersicht.hatAnteile()).isTrue();
	}

	/** {@code a} mit Preissteigerung, Garantie und Erinnerung. */
	private static VertragAnfrage mitSteigerung(VertragAnfrage a, VertragPreissteigerungAnfrage steigerung,
			LocalDate garantieBis, boolean erinnerung) {
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(), a.preise(),
				a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(), steigerung,
				garantieBis, erinnerung, a.kuendbar(), a.beginn(), a.laufzeitende(), a.verlaengerung(),
				a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	/** Monatlich 10 € ab heute, jedes Quartal 1 € teurer ab heute: Die Abbuchung heute rechnet schon mit 11 €. */
	private VertragAnfrage steigtAbHeute() {
		LocalDate heute = LocalDate.now(clock);
		// Seit einem Jahr: Am Beginn selbst erhöht die Prognose nicht (sein Preis ist der Eintrag dort).
		LocalDate beginn = heute.minusYears(1);
		return mitSteigerung(mit(anfrage(VERSICHERUNG), preis(beginn, "10.00"), Zahlweise.MONATLICH, beginn, null),
				new VertragPreissteigerungAnfrage(Steigerungsart.BETRAG, null, euro("1.00"),
						Erhoehungsrhythmus.VIERTELJAEHRLICH, heute), null, true);
	}

	@Test
	void rechnetMitDerPrognose() {
		LocalDate heute = LocalDate.now(clock);

		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(steigtAbHeute()));

		assertThat(vertrag.preissteigerung()).isEqualTo(new VertragPreissteigerungAntwort(Steigerungsart.BETRAG, null,
				euro("1.00"), Erhoehungsrhythmus.VIERTELJAEHRLICH, heute));
		assertThat(vertrag.berechnet().prognose()).containsExactly(new Prognosepreis(heute, euro("11.00")),
				new Prognosepreis(heute.plusMonths(3), euro("12.00")), new Prognosepreis(heute.plusMonths(6), euro("13.00")));
		assertThat(vertrag.berechnet().naechstePreisaenderung())
				.isEqualTo(new Preisaenderung(heute, euro("11.00"), Preisaenderungsart.PROGNOSE));
		assertThat(vertrag.berechnet().kostenImJahrMitPrognose()).isTrue();
		// Bekannter Preis von heute: 10 €, auch pro Monat.
		assertThat(vertrag.betrag()).isEqualTo(euro("10.00"));
		assertThat(vertrag.berechnet().kostenProMonat()).isEqualTo(euro("10.00"));
		assertThat(vertrag.berechnet().preisverlauf()).extracting(Preispunkt::art).containsExactly(Preispunktart.GEZAHLT,
				Preispunktart.PROGNOSE, Preispunktart.PROGNOSE, Preispunktart.PROGNOSE);
		assertThat(HaushaltKontext.fuer(haushalt, service::uebersicht).kostenImJahrMitPrognose()).isTrue();
		assertThat(HaushaltKontext.fuer(haushalt, () -> service.abrufen(vertrag.id()))).isEqualTo(vertrag);
	}

	@Test
	void ohneRegelKeinePrognose() {
		var vertrag = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		assertThat(vertrag.preissteigerung()).isNull();
		assertThat(vertrag.erinnerungSonderkuendigung()).isTrue();
		assertThat(vertrag.berechnet().prognose()).isEmpty();
		assertThat(vertrag.berechnet().kostenImJahrMitPrognose()).isFalse();
		assertThat(HaushaltKontext.fuer(haushalt, service::uebersicht).kostenImJahrMitPrognose()).isFalse();
	}

	/** Gleiche Regel (2.5 wie 2.50) behält die Version; Garantie und Erinnerung werden gespeichert. */
	@Test
	void regelAendernUndEntfernen() {
		LocalDate heute = LocalDate.now(clock);
		var regel = new VertragPreissteigerungAnfrage(Steigerungsart.PROZENT, new BigDecimal("2.5"), null,
				Erhoehungsrhythmus.JAEHRLICH, heute.plusMonths(2));
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(mitSteigerung(anfrage(VERSICHERUNG), regel,
				heute.plusMonths(1), false)));

		var gleich = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0,
				mitSteigerung(anfrage(VERSICHERUNG), new VertragPreissteigerungAnfrage(Steigerungsart.PROZENT,
						new BigDecimal("2.50"), null, Erhoehungsrhythmus.JAEHRLICH, heute.plusMonths(2)),
						heute.plusMonths(1), false))));
		var ohne = HaushaltKontext.fuer(haushalt, () -> service.aendern(angelegt.id(), aenderung(0,
				anfrage(VERSICHERUNG))));

		assertThat(angelegt.preissteigerung().prozent()).isEqualTo(new BigDecimal("2.50"));
		assertThat(angelegt.preisgarantieBis()).isEqualTo(heute.plusMonths(1));
		assertThat(angelegt.erinnerungSonderkuendigung()).isFalse();
		assertThat(gleich.version()).isZero();
		assertThat(ohne.version()).isEqualTo(1);
		assertThat(ohne.preissteigerung()).isNull();
		assertThat(ohne.preisgarantieBis()).isNull();
		assertThat(ohne.erinnerungSonderkuendigung()).isTrue();
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
				.isEqualTo(new VertragsuebersichtAntwort(0, List.of(), List.of(), false, List.of(), LocalDate.now(clock).getYear(), false,
						false, List.of()));
	}

	/** Die Werte von {@code a}, die die Vorschau braucht. */
	private static VertragVorschauAnfrage vorschau(VertragAnfrage a) {
		var steigerung = a.preissteigerung();
		return new VertragVorschauAnfrage(
				a.preise().stream().map(p -> new VertragVorschauPreis(p.gueltigAb(), p.betrag())).toList(),
				a.zahlweise(), a.anteilProzent(),
				a.einmalkosten().stream()
						.map(e -> new VertragVorschauEinmalkosten(e.bezeichnung(), e.betrag(), e.faelligAm())).toList(),
				steigerung == null ? null : new VertragVorschauPreissteigerung(steigerung.art(), steigerung.prozent(),
						steigerung.betrag(), steigerung.rhythmus(), steigerung.naechsteAm()),
				a.preisgarantieBis(), a.kuendbar(), a.beginn(), a.laufzeitende(), a.verlaengerung(),
				a.kuendigungsfristMonate(), a.gekuendigtZum());
	}

	/** Was die Vorschau für den gespeicherten Vertrag {@code v} zeigen muss. */
	private static VertragVorschauAntwort wieGespeichert(VertragAntwort v) {
		var berechnet = v.berechnet();
		return new VertragVorschauAntwort(v.betrag(), v.preise(), berechnet.kostenProMonat(),
				berechnet.eigenerAnteil() == null ? null
						: new VertragVorschauAntwort.VertragVorschauEigenerAnteil(berechnet.eigenerAnteil().betrag(),
								berechnet.eigenerAnteil().kostenProMonat()),
				berechnet.naechstePreisaenderung(), berechnet.prognose(),
				v.einmalkosten().isEmpty() ? null : berechnet.einmalkosten(), berechnet.kuendigung());
	}

	/** Dieselbe Berechnung: Für vollständige Angaben zeigt die Vorschau, was der gespeicherte Vertrag berechnet. */
	@Test
	void vorschauWieGespeicherterVertrag() {
		var a = anfrage(VERSICHERUNG);
		LocalDate heute = LocalDate.now(clock);
		var gekuendigt = new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				a.preise(), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(),
				a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), true, a.beginn(),
				a.laufzeitende(), a.verlaengerung(), a.kuendigungsfristMonate(), heute.plusDays(30), a.notiz());
		// Nicht kündbar mit Verlängerung und Frist: beides wird verworfen, auch die Frist in der Kündigung.
		var nichtKuendbar = new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				a.preise(), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(),
				a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), false, a.beginn(),
				a.laufzeitende(), a.verlaengerung(), 3, null, a.notiz());
		// Alles zusammen: mehrere Preise, Anteil, Einmalkosten, Preissteigerung mit Garantie.
		var preise = List.of(new VertragPreisAnfrage(heute.minusMonths(5), euro("10.00")),
				new VertragPreisAnfrage(heute.plusDays(40), euro("11.00")));
		var voll = mitSteigerung(mitEinmalkosten(mitAnteil(mit(anfrage(VERSICHERUNG), preise, Zahlweise.MONATLICH,
						heute.minusMonths(5), null), "33.3"), einmal("Router", "99.99", heute)),
				new VertragPreissteigerungAnfrage(Steigerungsart.PROZENT, new BigDecimal("2.5"), null,
						Erhoehungsrhythmus.JAEHRLICH, heute.plusMonths(2)), heute.plusMonths(3), true);

		for (var anfrage : List.of(a, gekuendigt, nichtKuendbar, voll, steigtAbHeute())) {
			var gespeichert = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage));

			assertThat(service.vorschau(vorschau(anfrage))).isEqualTo(wieGespeichert(gespeichert));
		}
		assertThat(service.vorschau(vorschau(nichtKuendbar)).kuendigung().fristMonate()).isNull();
		assertThat(service.vorschau(vorschau(voll)).prognose()).hasSize(3);
		assertThat(service.vorschau(vorschau(voll)).eigenerAnteil()).isNotNull();
	}

	@Test
	void vorschauMitFehlendenWerten() {
		var leer = new VertragVorschauAnfrage(null, null, null, null, null, null, true, null, null, null, null, null);

		var vorschau = service.vorschau(leer);

		assertThat(vorschau.kuendigung().art()).isEqualTo(Kuendigungsart.KEINE_LAUFZEIT);
		assertThat(vorschau).isEqualTo(new VertragVorschauAntwort(null, List.of(), null, null, null, List.of(), null,
				vorschau.kuendigung()));
	}

	/** Was fehlt, bleibt leer; der Rest wird trotzdem berechnet. */
	@Test
	void vorschauMitTeilweisenAngaben() {
		LocalDate heute = LocalDate.now(clock);
		var preis = List.of(new VertragVorschauPreis(heute, euro("10.00")));
		var ohneBeginn = new VertragVorschauAnfrage(preis, Zahlweise.MONATLICH, new BigDecimal("50"), null, null,
				null, true, null, null, null, null, null);
		var ohneZahlweise = new VertragVorschauAnfrage(preis, null, new BigDecimal("50"), null,
				null, null, true, heute, null, null, null, null);

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
		var preise = List.of(new VertragVorschauPreis(heute, euro("10.00")), new VertragVorschauPreis(heute.plusDays(5), null),
				new VertragVorschauPreis(null, euro("11.00")));
		var einmalkosten = List.of(new VertragVorschauEinmalkosten("Router", euro("99.99"), null),
				new VertragVorschauEinmalkosten(" ", euro("5.00"), heute),
				new VertragVorschauEinmalkosten("Anschluss", euro("49.00"), heute));
		// Prozent ohne Prozentsatz; der Betrag gehört zur anderen Art und zählt nicht.
		var steigerung = new VertragVorschauPreissteigerung(Steigerungsart.PROZENT, null, euro("1.00"),
				Erhoehungsrhythmus.JAEHRLICH, heute);

		var vorschau = service.vorschau(new VertragVorschauAnfrage(preise, Zahlweise.MONATLICH,
				null, einmalkosten, steigerung, null, true, heute, null, null, null, null));

		assertThat(vorschau.betrag()).isEqualTo(euro("10.00"));
		assertThat(vorschau.preise()).containsExactly(new VertragPreisAntwort(heute, euro("10.00"), Preiszeilenart.AKTUELL));
		assertThat(vorschau.naechstePreisaenderung()).isNull();
		assertThat(vorschau.prognose()).isEmpty();
		assertThat(vorschau.einmalkosten()).isEqualTo(euro("49.00"));
	}

	/** Ohne Preis summieren die Einmalkosten in ihrer eigenen Währung; ohne vollständige Zeile bleiben sie leer. */
	@Test
	void vorschauEinmalkostenOhnePreis() {
		var nurEinmalkosten = new VertragVorschauAnfrage(null, null, null,
				List.of(new VertragVorschauEinmalkosten("Router", euro("99.99"), LocalDate.now(clock))), null, null, true,
				null, null, null, null, null);
		var nichtsVollstaendig = new VertragVorschauAnfrage(List.of(new VertragVorschauPreis(null, euro("10.00"))), null,
				null, List.of(new VertragVorschauEinmalkosten("Router", null, null)), null, null, true, null, null,
				null, null, null);

		assertThat(service.vorschau(nurEinmalkosten).einmalkosten()).isEqualTo(euro("99.99"));
		assertThat(service.vorschau(nurEinmalkosten).betrag()).isNull();
		assertThat(service.vorschau(nichtsVollstaendig).einmalkosten()).isNull();
	}

	/** Die Angaben von {@code anfrage} als Änderung mit {@code version}. */
	static VertragAenderung aenderung(long version, VertragAnfrage a) {
		return new VertragAenderung(version, a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				a.preise(), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(), a.beginn(), a.laufzeitende(),
				a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	/** Wie {@link #anfrage}, aber monatlich 50 € statt jährlich 586,80 €. */
	private static VertragAnfrage monatlich() {
		var a = anfrage(VERSICHERUNG);
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				preis(a.beginn(), "50.00"),
				Zahlweise.MONATLICH, a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(), a.beginn(), a.laufzeitende(),
				a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz());
	}

	@Test
	void aendertVertragUndLiefertNeueVersion() {
		var angelegt = HaushaltKontext.fuer(haushalt, () -> service.anlegen(anfrage(VERSICHERUNG)));

		var geaendert = HaushaltKontext.fuer(haushalt,
				() -> service.aendern(angelegt.id(), aenderung(angelegt.version(), monatlich())));

		assertThat(geaendert.version()).isEqualTo(angelegt.version() + 1);
		assertThat(geaendert.zahlweise()).isEqualTo(Zahlweise.MONATLICH);
		// Zwölf Abbuchungen im Jahr am 1. (Beginn 01.01.2023).
		assertThat(geaendert.berechnet().kostenImJahr()).isEqualTo(euro("600.00"));
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
				a.preise(), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), false, a.beginn(), a.laufzeitende(),
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
		// Beginn vor einem Jahr im Raster von heute + 10 Tagen: ohne Laufzeitende enden die Verträge heute + 9 Tage.
		LocalDate beginn = LocalDate.now(clock).plusDays(10).minusYears(1);
		service.anlegen(new VertragAnfrage(anbieter, bezeichnung, VERSICHERUNG, null, preis(beginn, "10.00"), zahlweise,
				null, null, List.of(), null, null, true,
				kuendbar, beginn, laufzeitende, verlaengerung, frist,
				gekuendigtZum, null));
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
