package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.FachmodulTest;
import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsstatus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Wiederaufnahmetermin;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Erhoehungsrhythmus;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Steigerungsart;
import de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau.VertragVorschauAnfrage;
import de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau.VertragVorschauPreis;
import de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau.VertragVorschauService;
import de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau.VertragVorschauUnterbrechung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Wiederaufnahme nach einer Kündigung und Unterbrechungen beim Ändern und in der Vorschau (#166, ADR 0023). Die Daten
 * hängen am heutigen Tag: Ein monatliches Abo seit zwei Jahren endete vor rund vier Monaten.
 */
@FachmodulTest
class VertragWiederaufnahmeTests {

	@Autowired
	private VertragService service;

	@Autowired
	private VertragVorschauService vorschauService;

	@Autowired
	private Clock clock;

	private final UUID haushalt = UUID.randomUUID();

	private LocalDate heute;

	/** Vertragsbeginn, ein Monatserster vor zwei Jahren. */
	private LocalDate beginn;

	/** Letzter Tag des beendeten Vertrags, ein Monatsletzter vor rund vier Monaten. */
	private LocalDate ende;

	/** Wiederaufnahme mit Lücke: zwei Monate nach dem Ende, noch vor heute. */
	private LocalDate wieder;

	@BeforeEach
	void tage() {
		heute = LocalDate.now(clock);
		beginn = heute.minusYears(2).withDayOfMonth(1);
		ende = heute.withDayOfMonth(1).minusMonths(3).minusDays(1);
		wieder = ende.plusDays(1).plusMonths(2);
	}

	private <T> T imHaushalt(Supplier<T> aufruf) {
		return HaushaltKontext.fuer(haushalt, aufruf::get);
	}

	/** Monatlich 10 €, jederzeit zum Ende des Zahlungsintervalls kündbar. */
	private VertragAnfrage abo(boolean kuendbar, LocalDate gekuendigtZum, List<VertragPreisAnfrage> preise,
			VertragPreissteigerungAnfrage steigerung) {
		return new VertragAnfrage("Streamingdienst", null, VertragServiceTests.VERSICHERUNG, null, preise,
				Zahlweise.MONATLICH, null, null, List.of(), steigerung, null, true, kuendbar, beginn, null,
				Verlaengerung.ZAHLUNGSINTERVALL, 0, gekuendigtZum, null);
	}

	private VertragAnfrage beendet() {
		return abo(true, ende, VertragServiceTests.preis(beginn, "10.00"), null);
	}

	private VertragAntwort anlegen(VertragAnfrage anfrage) {
		return imHaushalt(() -> service.anlegen(anfrage));
	}

	private VertragAntwort wiederaufnehmen(VertragAntwort vertrag, LocalDate datum, Betrag betrag) {
		return imHaushalt(() -> service.wiederaufnehmen(vertrag.id(), new VertragWiederaufnahme(vertrag.version(),
				datum, null, Verlaengerung.ZAHLUNGSINTERVALL, 0, betrag)));
	}

	/** Die Angaben von {@code anfrage} als Änderung, die nur die Unterbrechungen mit diesen IDs behält. */
	private static VertragAenderung aenderung(long version, VertragAnfrage a, List<UUID> unterbrechungIds) {
		return new VertragAenderung(version, a.anbieter(), a.bezeichnung(), a.kategorieId(),
				a.vertragsnummer(), a.preise(), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(), a.einmalkosten(),
				a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(), a.beginn(),
				a.laufzeitende(), a.verlaengerung(), a.kuendigungsfristMonate(), a.gekuendigtZum(), a.notiz(),
				unterbrechungIds);
	}

	/** Wie {@link #abo}, aber mit anderem Beginn, Laufzeitende und Kündigung. */
	private VertragAnfrage mit(VertragAnfrage a, LocalDate beginn, LocalDate laufzeitende, LocalDate gekuendigtZum) {
		return new VertragAnfrage(a.anbieter(), a.bezeichnung(), a.kategorieId(), a.vertragsnummer(),
				VertragServiceTests.preis(beginn, "10.00"), a.zahlweise(), a.zahlungsweg(), a.anteilProzent(),
				a.einmalkosten(), a.preissteigerung(), a.preisgarantieBis(), a.erinnerungSonderkuendigung(), a.kuendbar(),
				beginn, laufzeitende, a.verlaengerung(), a.kuendigungsfristMonate(), gekuendigtZum, a.notiz());
	}

	private static void feldfehler(Runnable aufruf, String feld, String code) {
		assertThatThrownBy(aufruf::run).isInstanceOfSatisfying(UngueltigeEingabeException.class, e -> {
			assertThat(e.getFeld()).isEqualTo(feld);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	@Test
	void beendetLaesstSichWiederAufnehmen() {
		var vertrag = anlegen(beendet());

		assertThat(vertrag.berechnet().status()).isEqualTo(Vertragsstatus.BEENDET);
		assertThat(vertrag.berechnet().wiederaufnahme()).isEqualTo(new Wiederaufnahmetermin(ende.plusDays(1), heute));
		assertThat(vertrag.berechnet().aktuellerBeginn()).isEqualTo(beginn);
		assertThat(vertrag.unterbrechungen()).isEmpty();
	}

	@Test
	void mitLueckeEntstehtEineUnterbrechung() {
		var vertrag = anlegen(beendet());

		var wiederaufgenommen = wiederaufnehmen(vertrag, wieder, null);

		assertThat(wiederaufgenommen.version()).isEqualTo(vertrag.version() + 1);
		assertThat(wiederaufgenommen.gekuendigtZum()).isNull();
		assertThat(wiederaufgenommen.unterbrechungen())
				.extracting(VertragUnterbrechungAntwort::von, VertragUnterbrechungAntwort::bis,
						VertragUnterbrechungAntwort::art)
				.containsExactly(tuple(ende.plusDays(1), wieder.minusDays(1), Unterbrechungsart.KUENDIGUNG));
		assertThat(wiederaufgenommen.berechnet().status()).isEqualTo(Vertragsstatus.AKTIV);
		assertThat(wiederaufgenommen.berechnet().aktuellerBeginn()).isEqualTo(wieder);
		assertThat(wiederaufgenommen.berechnet().wiederaufnahme()).isNull();
		assertThat(wiederaufgenommen.berechnet().vertragsende()).isNull();
		assertThat(wiederaufgenommen.betrag()).isEqualTo(euro("10.00"));
		assertThat(imHaushalt(() -> service.abrufen(vertrag.id()))).isEqualTo(wiederaufgenommen);
	}

	@Test
	void ohneLueckeNahtlosImAltenTakt() {
		var vertrag = anlegen(beendet());

		var wiederaufgenommen = wiederaufnehmen(vertrag, ende.plusDays(1), null);

		assertThat(wiederaufgenommen.unterbrechungen()).isEmpty();
		assertThat(wiederaufgenommen.berechnet().aktuellerBeginn()).isEqualTo(beginn);
		assertThat(wiederaufgenommen.berechnet().status()).isEqualTo(Vertragsstatus.AKTIV);
		// Wie ein Vertrag, der nie gekündigt war.
		var durchgehend = anlegen(abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null));
		assertThat(wiederaufgenommen.berechnet().kostenImJahr()).isEqualTo(durchgehend.berechnet().kostenImJahr());
	}

	@Test
	void neuerBetragWirdEinPreisAbDemDatum() {
		var vertrag = anlegen(beendet());

		var wiederaufgenommen = wiederaufnehmen(vertrag, wieder, euro("12.99"));

		assertThat(wiederaufgenommen.preise())
				.extracting(VertragPreisAntwort::gueltigAb, VertragPreisAntwort::betrag, VertragPreisAntwort::automatisch)
				.containsExactly(tuple(beginn, euro("10.00"), false), tuple(wieder, euro("12.99"), false));
		assertThat(wiederaufgenommen.betrag()).isEqualTo(euro("12.99"));
	}

	@Test
	void uebernimmtLaufzeitUndVerlaengerung() {
		var vertrag = anlegen(beendet());
		LocalDate laufzeitende = wieder.plusYears(1).minusDays(1);

		var wiederaufgenommen = imHaushalt(() -> service.wiederaufnehmen(vertrag.id(), new VertragWiederaufnahme(
				vertrag.version(), wieder, laufzeitende, Verlaengerung.MONATLICH, 1, null)));

		assertThat(wiederaufgenommen.laufzeitende()).isEqualTo(laufzeitende);
		assertThat(wiederaufgenommen.verlaengerung()).isEqualTo(Verlaengerung.MONATLICH);
		assertThat(wiederaufgenommen.kuendigungsfristMonate()).isEqualTo(1);
		assertThat(wiederaufgenommen.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.KUENDIGEN_BIS);
		assertThat(wiederaufgenommen.berechnet().kuendigung().zum()).isEqualTo(laufzeitende);
	}

	@Test
	void nichtKuendbarOhneVerlaengerung() {
		var vertrag = anlegen(abo(false, ende, VertragServiceTests.preis(beginn, "18.36"), null));

		var wiederaufgenommen = imHaushalt(() -> service.wiederaufnehmen(vertrag.id(),
				new VertragWiederaufnahme(vertrag.version(), wieder, null, null, 3, null)));

		assertThat(wiederaufgenommen.berechnet().status()).isEqualTo(Vertragsstatus.AKTIV);
		assertThat(wiederaufgenommen.verlaengerung()).isNull();
		assertThat(wiederaufgenommen.kuendigungsfristMonate()).isNull();
		assertThat(wiederaufgenommen.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.NICHT_KUENDBAR);
	}

	@Test
	void datumInDerZukunftUnterbrochen() {
		var vertrag = anlegen(beendet());
		LocalDate spaeter = heute.plusMonths(1);

		var wiederaufgenommen = wiederaufnehmen(vertrag, spaeter, null);

		assertThat(wiederaufgenommen.berechnet().status()).isEqualTo(Vertragsstatus.UNTERBROCHEN);
		assertThat(wiederaufgenommen.berechnet().kuendigung().art()).isEqualTo(Kuendigungsart.WIEDER_AB);
		assertThat(wiederaufgenommen.berechnet().kuendigung().zum()).isEqualTo(spaeter);
		// Unterbrochene zählen wie beendete nicht als laufend.
		assertThat(imHaushalt(service::uebersicht).laufendeVertraege()).isZero();
		// Ein zweites Mal geht erst, wenn er wieder gekündigt ist.
		assertThatThrownBy(() -> wiederaufnehmen(wiederaufgenommen, spaeter.plusDays(1), null))
				.isInstanceOf(WiederaufnahmeNichtMoeglichException.class);
	}

	@Test
	void aktivUndStorniertNichtMoeglich() {
		var aktiv = anlegen(abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null));
		var storniert = anlegen(abo(true, beginn.minusDays(1), VertragServiceTests.preis(beginn, "10.00"), null));

		assertThat(aktiv.berechnet().wiederaufnahme()).isNull();
		assertThat(storniert.berechnet().status()).isEqualTo(Vertragsstatus.STORNIERT);
		assertThatThrownBy(() -> wiederaufnehmen(aktiv, heute, null))
				.isInstanceOf(WiederaufnahmeNichtMoeglichException.class);
		assertThatThrownBy(() -> wiederaufnehmen(storniert, heute, null))
				.isInstanceOf(WiederaufnahmeNichtMoeglichException.class);
	}

	@Test
	void veralteteVersionKonflikt() {
		var vertrag = anlegen(beendet());
		imHaushalt(() -> service.aendern(vertrag.id(), aenderung(vertrag.version(), mit(beendet(), beginn, null, ende.minusDays(1)),
				List.of())));

		assertThatThrownBy(() -> wiederaufnehmen(vertrag, wieder, null)).isInstanceOf(KonfliktException.class);
		assertThat(imHaushalt(() -> service.abrufen(vertrag.id())).unterbrechungen()).isEmpty();
	}

	@Test
	void feldfehler() {
		var vertrag = anlegen(beendet());

		assertThatThrownBy(() -> wiederaufnehmen(vertrag, ende, null))
				.isInstanceOfSatisfying(UngueltigeEingabeException.class, e -> {
					assertThat(e.getFeld()).isEqualTo("datum");
					assertThat(e.getCode()).isEqualTo("NachVertragsende");
					assertThat(e.getParameter()).containsEntry("vertragsende", ende);
				});
		feldfehler(() -> imHaushalt(() -> service.wiederaufnehmen(vertrag.id(), new VertragWiederaufnahme(
				vertrag.version(), wieder, wieder.minusDays(1), Verlaengerung.MONATLICH, 1, null))),
				"laufzeitende", "LaufzeitendeNachBeginn");
		feldfehler(() -> imHaushalt(() -> service.wiederaufnehmen(vertrag.id(), new VertragWiederaufnahme(
				vertrag.version(), wieder, null, Verlaengerung.KEINE, 1, null))),
				"verlaengerung", "LaufzeitendeBeiEnde");
		feldfehler(() -> imHaushalt(() -> service.wiederaufnehmen(vertrag.id(), new VertragWiederaufnahme(
				vertrag.version(), wieder, null, null, 1, null))),
				"verlaengerung", "NotNull");
		assertThat(imHaushalt(() -> service.abrufen(vertrag.id()))).isEqualTo(vertrag);
	}

	@Test
	void eingetragenerPreisAmDatumBleibtEindeutig() {
		var preise = List.of(new VertragPreisAnfrage(beginn, euro("10.00"), null),
				new VertragPreisAnfrage(wieder, euro("11.00"), null));
		var vertrag = anlegen(abo(true, ende, preise, null));

		feldfehler(() -> wiederaufnehmen(vertrag, wieder, euro("12.00")), "betrag", "GueltigAbEindeutig");
		// Ohne neuen Betrag gilt der eingetragene.
		assertThat(wiederaufnehmen(vertrag, wieder, null).betrag()).isEqualTo(euro("11.00"));
	}

	/** Jährlich +1 € ab dem Monat nach dem Ende: Der Termin fällt in die Unterbrechung oder auf die Wiederaufnahme. */
	private VertragPreissteigerungAnfrage steigerungAb(LocalDate naechsteAm) {
		return new VertragPreissteigerungAnfrage(Steigerungsart.BETRAG, null, euro("1.00"),
				Erhoehungsrhythmus.JAEHRLICH, naechsteAm);
	}

	@Test
	void faelligeStufenWerdenGespeichert() {
		LocalDate termin = ende.plusDays(1).plusMonths(1);
		var vertrag = anlegen(abo(true, ende, VertragServiceTests.preis(beginn, "10.00"), steigerungAb(termin)));
		// Nach dem Ende ist nichts fällig.
		assertThat(vertrag.preise()).hasSize(1);

		var wiederaufgenommen = wiederaufnehmen(vertrag, wieder, null);

		// Preise gelten nach Kalender, auch in der Unterbrechung.
		assertThat(wiederaufgenommen.preise())
				.extracting(VertragPreisAntwort::gueltigAb, VertragPreisAntwort::betrag, VertragPreisAntwort::automatisch)
				.containsExactly(tuple(beginn, euro("10.00"), false), tuple(termin, euro("11.00"), true));
		assertThat(wiederaufgenommen.betragErrechnet()).isFalse();
	}

	@Test
	void neuerBetragErsetztFaelligeStufeAmDatum() {
		var vertrag = anlegen(abo(true, ende, VertragServiceTests.preis(beginn, "10.00"), steigerungAb(wieder)));

		var wiederaufgenommen = wiederaufnehmen(vertrag, wieder, euro("12.00"));

		assertThat(wiederaufgenommen.preise())
				.extracting(VertragPreisAntwort::gueltigAb, VertragPreisAntwort::betrag, VertragPreisAntwort::automatisch)
				.containsExactly(tuple(beginn, euro("10.00"), false), tuple(wieder, euro("12.00"), false));
	}

	@Test
	void neuerBetragErsetztGespeichertenAutomatischenPreis() {
		// Läuft zunächst, die Stufe am Tag der späteren Wiederaufnahme ist gespeichert; dann zum Ende gekündigt.
		var anfrage = abo(true, null, VertragServiceTests.preis(beginn, "10.00"), steigerungAb(wieder));
		var laufend = anlegen(anfrage);
		assertThat(laufend.preise()).extracting(VertragPreisAntwort::automatisch).containsExactly(false, true);
		var preise = laufend.preise().stream()
				.map(preis -> new VertragPreisAnfrage(preis.gueltigAb(), preis.betrag(), preis.automatisch()))
				.toList();
		var gekuendigt = new VertragAnfrage(anfrage.anbieter(), null, anfrage.kategorieId(), null, preise,
				anfrage.zahlweise(), null, null, List.of(), anfrage.preissteigerung(), null, true, true, beginn, null,
				Verlaengerung.ZAHLUNGSINTERVALL, 0, ende, null);
		var beendet = imHaushalt(() -> service.aendern(laufend.id(), aenderung(laufend.version(), gekuendigt, List.of())));
		assertThat(beendet.preise()).extracting(VertragPreisAntwort::automatisch).containsExactly(false, true);

		var wiederaufgenommen = wiederaufnehmen(beendet, wieder, euro("12.00"));

		assertThat(wiederaufgenommen.preise())
				.extracting(VertragPreisAntwort::gueltigAb, VertragPreisAntwort::betrag, VertragPreisAntwort::automatisch)
				.containsExactly(tuple(beginn, euro("10.00"), false), tuple(wieder, euro("12.00"), false));
	}

	@Test
	void unterbrechungEntfernenLegtPhasenZusammen() {
		var wiederaufgenommen = wiederaufnehmen(anlegen(beendet()), wieder, null);
		var durchgehend = anlegen(abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null));
		var id = wiederaufgenommen.unterbrechungen().getFirst().id();

		var behalten = imHaushalt(() -> service.aendern(wiederaufgenommen.id(),
				aenderung(wiederaufgenommen.version(), abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null),
						List.of(id))));
		assertThat(behalten.version()).isEqualTo(wiederaufgenommen.version());
		assertThat(behalten.unterbrechungen()).extracting(VertragUnterbrechungAntwort::id).containsExactly(id);

		var entfernt = imHaushalt(() -> service.aendern(wiederaufgenommen.id(),
				aenderung(behalten.version(), abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null), List.of())));

		assertThat(entfernt.version()).isEqualTo(behalten.version() + 1);
		assertThat(entfernt.unterbrechungen()).isEmpty();
		assertThat(entfernt.berechnet().aktuellerBeginn()).isEqualTo(beginn);
		assertThat(entfernt.berechnet().kostenImJahr()).isEqualTo(durchgehend.berechnet().kostenImJahr());
	}

	@Test
	void unbekannteUnterbrechungUngueltig() {
		var wiederaufgenommen = wiederaufnehmen(anlegen(beendet()), wieder, null);
		var andere = wiederaufnehmen(anlegen(beendet()), wieder, null).unterbrechungen().getFirst().id();

		feldfehler(() -> imHaushalt(() -> service.aendern(wiederaufgenommen.id(), aenderung(wiederaufgenommen.version(),
				abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null),
				List.of(wiederaufgenommen.unterbrechungen().getFirst().id(), andere)))), "unterbrechungIds[1]",
				"Unbekannt");
		assertThat(imHaushalt(() -> service.abrufen(wiederaufgenommen.id()))).isEqualTo(wiederaufgenommen);
	}

	@Test
	void entfernenMitVeralteterVersionKonflikt() {
		var wiederaufgenommen = wiederaufnehmen(anlegen(beendet()), wieder, null);
		var veraltet = aenderung(wiederaufgenommen.version() - 1,
				abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null), List.of());

		assertThatThrownBy(() -> imHaushalt(() -> service.aendern(wiederaufgenommen.id(), veraltet)))
				.isInstanceOf(KonfliktException.class);
		assertThat(imHaushalt(() -> service.abrufen(wiederaufgenommen.id())).unterbrechungen()).hasSize(1);
	}

	@Test
	void aenderungPasstZuDenUnterbrechungen() {
		var wiederaufgenommen = wiederaufnehmen(anlegen(beendet()), wieder, null);
		var ids = List.of(wiederaufgenommen.unterbrechungen().getFirst().id());
		var laufend = abo(true, null, VertragServiceTests.preis(beginn, "10.00"), null);

		feldfehler(() -> imHaushalt(() -> service.aendern(wiederaufgenommen.id(), aenderung(wiederaufgenommen.version(),
				mit(laufend, ende.plusDays(1), null, null), ids))), "beginn", "BeginnVorUnterbrechung");
		feldfehler(() -> imHaushalt(() -> service.aendern(wiederaufgenommen.id(), aenderung(wiederaufgenommen.version(),
				mit(laufend, beginn, null, wieder.minusDays(1)), ids))), "gekuendigtZum", "KuendigungNachUnterbrechung");
		feldfehler(() -> imHaushalt(() -> service.aendern(wiederaufgenommen.id(), aenderung(wiederaufgenommen.version(),
				mit(laufend, beginn, wieder.minusDays(1), null), ids))), "laufzeitende",
				"LaufzeitendeNachAktuellemBeginn");
		// Am Tag der Wiederaufnahme ist beides erlaubt.
		var gekuendigt = imHaushalt(() -> service.aendern(wiederaufgenommen.id(), aenderung(wiederaufgenommen.version(),
				mit(laufend, beginn, wieder, wieder), ids)));
		assertThat(gekuendigt.berechnet().status()).isEqualTo(Vertragsstatus.BEENDET);
	}

	@Test
	void vorschauRechnetMitDenUnterbrechungen() {
		var unterbrechung = new VertragVorschauUnterbrechung(ende.plusDays(1), heute.plusMonths(1).minusDays(1),
				Unterbrechungsart.KUENDIGUNG);
		var ohne = new VertragVorschauAnfrage(List.of(new VertragVorschauPreis(beginn, euro("10.00"), null)),
				Zahlweise.MONATLICH, null, null, null, null, null, true, beginn, null, Verlaengerung.ZWOELF_MONATE, 0,
				null, null);
		var mitUnterbrechung = new VertragVorschauAnfrage(ohne.preise(), ohne.zahlweise(), null, null, null, null, null,
				true, beginn, null, Verlaengerung.ZWOELF_MONATE, 0, null, List.of(unterbrechung));

		assertThat(vorschauService.vorschau(ohne).kuendigung().art()).isEqualTo(Kuendigungsart.KUENDIGEN_BIS);
		var vorschau = vorschauService.vorschau(mitUnterbrechung);
		assertThat(vorschau.kuendigung().art()).isEqualTo(Kuendigungsart.WIEDER_AB);
		assertThat(vorschau.kuendigung().zum()).isEqualTo(heute.plusMonths(1));
		assertThat(vorschau.naechstePreisaenderung()).isNull();
	}

}
