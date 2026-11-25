package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisherkunft;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieService;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Unterbrechungsregeln;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Verträge des aktuellen Haushalts; die Trennung der Haushalte übernimmt {@code FachEntity}. */
@Service
class VertragService {

	/** Ohne Groß-/Kleinschreibung und Akzente, „Ärzte“ also bei A; {@code compare} ist synchronisiert. */
	private static final Collator NAME = Collator.getInstance(Locale.GERMAN);

	static {
		NAME.setStrength(Collator.PRIMARY);
	}

	/** Nach Dringlichkeit der Kündigung, dann nach dem angezeigten Namen, zuletzt nach ID (fest bei gleichen Namen). */
	private static final Comparator<VertragAntwort> REIHENFOLGE = Comparator
			.comparing((VertragAntwort v) -> v.berechnet().kuendigung(), Kuendigung.NACH_DRINGLICHKEIT)
			.thenComparing(VertragService::anzeigeName, NAME)
			.thenComparing(VertragAntwort::id);

	private final VertragRepository repository;

	private final VertragKategorieService kategorieService;

	private final Clock clock;

	VertragService(VertragRepository repository, VertragKategorieService kategorieService, Clock clock) {
		this.repository = repository;
		this.kategorieService = kategorieService;
		this.clock = clock;
	}

	/**
	 * @throws UngueltigeEingabeException wenn die Kategorie unbekannt ist oder einem anderen Haushalt gehört
	 */
	@Transactional
	VertragAntwort anlegen(VertragAnfrage anfrage) {
		var berechnung = berechnung();
		var vertrag = repository.save(new Vertrag(anfrage, kategorie(anfrage.kategorieId()), berechnung));
		return VertragAntwort.aus(vertrag, berechnung);
	}

	/**
	 * @throws NichtGefundenException wenn es den Vertrag nicht gibt oder er einem anderen Haushalt gehört;
	 *         beides ist bewusst nicht zu unterscheiden
	 */
	@Transactional(readOnly = true)
	VertragAntwort abrufen(UUID id) {
		return VertragAntwort.aus(vertrag(id), berechnung());
	}

	/**
	 * Ersetzt alle Angaben des Vertrags und legt fällige Stufen der Preissteigerung an (siehe {@link Vertrag#setzen}).
	 * Unterbrechungen, deren ID in {@code unterbrechungIds} fehlt, fallen weg; zu den verbleibenden müssen Beginn,
	 * Kündigung und Laufzeitende passen ({@link Unterbrechungsregeln}).
	 *
	 * @throws NichtGefundenException wie bei {@link #abrufen}
	 * @throws KonfliktException wenn {@code version} veraltet ist, also inzwischen jemand anderes geändert hat
	 * @throws UngueltigeEingabeException wie bei {@link #anlegen}, bei einer unbekannten ID in
	 *         {@code unterbrechungIds} und wenn die Angaben nicht zu den verbleibenden Unterbrechungen passen
	 */
	@Transactional
	VertragAntwort aendern(UUID id, VertragAenderung aenderung) {
		var vertrag = vertrag(id);
		vertrag.pruefeVersion(aenderung.version());
		var unbekannt = vertrag.unterbrechungenBehalten(aenderung.unterbrechungIds());
		if (!unbekannt.isEmpty()) {
			throw new UngueltigeEingabeException("unterbrechungIds[%d]".formatted(
					aenderung.unterbrechungIds().indexOf(unbekannt.getFirst())), "Unbekannt",
					"Interruption %s not found in contract %s.".formatted(unbekannt.getFirst(), id));
		}
		pruefeUnterbrechungen(aenderung, vertrag.daten().unterbrechungen());
		var berechnung = berechnung();
		vertrag.setzen(aenderung, kategorie(aenderung.kategorieId()), berechnung);
		// Hibernate erhöht die Version erst beim Schreiben; die Antwort braucht schon die neue.
		repository.flush();
		return VertragAntwort.aus(vertrag, berechnung);
	}

	/**
	 * Nimmt einen gekündigten oder beendeten Vertrag wieder auf (siehe {@link Vertrag#wiederaufnehmen}).
	 *
	 * @throws NichtGefundenException wie bei {@link #abrufen}
	 * @throws KonfliktException wenn {@code version} veraltet ist
	 * @throws WiederaufnahmeNichtMoeglichException wenn der Vertrag weder gekündigt noch beendet ist oder kein
	 *         Vertragsende hat
	 * @throws UngueltigeEingabeException wenn eine Angabe nicht passt, siehe {@code docs/api.md}
	 */
	@Transactional
	VertragAntwort wiederaufnehmen(UUID id, VertragWiederaufnahme wiederaufnahme) {
		var vertrag = vertrag(id);
		vertrag.pruefeVersion(wiederaufnahme.version());
		var berechnung = berechnung();
		var daten = vertrag.daten();
		if (berechnung.laufzeit().wiederaufnahme(daten).isEmpty()) {
			throw new WiederaufnahmeNichtMoeglichException(
					"Contract %s with status %s cannot be resumed.".formatted(id, berechnung.laufzeit().status(daten)));
		}
		LocalDate vertragsende = berechnung.laufzeit().vertragsende(daten).orElseThrow();
		pruefeWiederaufnahme(vertrag, wiederaufnahme, vertragsende);
		vertrag.wiederaufnehmen(wiederaufnahme.datum(), vertragsende, wiederaufnahme.laufzeitende(),
				wiederaufnahme.verlaengerung(), wiederaufnahme.kuendigungsfristMonate(), wiederaufnahme.betrag(),
				berechnung);
		repository.flush();
		return VertragAntwort.aus(vertrag, berechnung);
	}

	/** Die Regeln der Wiederaufnahme, die den gespeicherten Vertrag brauchen; die Reihenfolge der Felder im Dialog. */
	private static void pruefeWiederaufnahme(Vertrag vertrag, VertragWiederaufnahme wiederaufnahme,
			LocalDate vertragsende) {
		LocalDate datum = wiederaufnahme.datum();
		if (!datum.isAfter(vertragsende)) {
			throw new UngueltigeEingabeException("datum", "NachVertragsende", Map.of("vertragsende", vertragsende),
					"Resumption %s must be after the end of the contract (%s).".formatted(datum, vertragsende));
		}
		if (vertrag.isKuendbar()) {
			if (wiederaufnahme.verlaengerung() == null) {
				throw new UngueltigeEingabeException("verlaengerung", "NotNull",
						"A cancellable contract needs a renewal.");
			}
			if (wiederaufnahme.verlaengerung() == Verlaengerung.KEINE && wiederaufnahme.laufzeitende() == null) {
				throw new UngueltigeEingabeException("verlaengerung", "LaufzeitendeBeiEnde",
						"Without renewal the contract needs an end of term.");
			}
		}
		if (wiederaufnahme.laufzeitende() != null && wiederaufnahme.laufzeitende().isBefore(datum)) {
			throw new UngueltigeEingabeException("laufzeitende", "LaufzeitendeNachBeginn",
					"End of term %s is before the resumption %s.".formatted(wiederaufnahme.laufzeitende(), datum));
		}
		var betrag = wiederaufnahme.betrag();
		if (betrag != null) {
			var waehrung = vertrag.preisliste().waehrung();
			if (!betrag.waehrung().equals(waehrung)) {
				throw new UngueltigeEingabeException("betrag.waehrung", "GleicheWaehrung",
						"Currency %s differs from the prices (%s).".formatted(betrag.waehrung(), waehrung));
			}
			// Ein automatischer Preis an dem Tag wird ersetzt, ein eingetragener nicht.
			if (vertrag.preisliste().preise().stream().anyMatch(preis -> preis.gueltigAb().equals(datum)
					&& preis.herkunft() == Preisherkunft.EINGETRAGEN)) {
				throw new UngueltigeEingabeException("betrag", "GueltigAbEindeutig",
						"There is already a price on %s.".formatted(datum));
			}
		}
	}

	/** Die {@link Unterbrechungsregeln} gegen die verbleibenden gespeicherten Unterbrechungen. */
	private static void pruefeUnterbrechungen(VertragAenderung aenderung, List<Unterbrechung> unterbrechungen) {
		if (!Unterbrechungsregeln.beginnVorUnterbrechung(aenderung.beginn(), unterbrechungen)) {
			throw new UngueltigeEingabeException("beginn", "BeginnVorUnterbrechung",
					"Start %s is not before the first interruption.".formatted(aenderung.beginn()));
		}
		if (!Unterbrechungsregeln.kuendigungNachUnterbrechung(aenderung.gekuendigtZum(), unterbrechungen)) {
			throw new UngueltigeEingabeException("gekuendigtZum", "KuendigungNachUnterbrechung",
					"Cancellation %s is before the last resumption.".formatted(aenderung.gekuendigtZum()));
		}
		if (!Unterbrechungsregeln.laufzeitendeNachAktuellemBeginn(aenderung.laufzeitende(), unterbrechungen)) {
			throw new UngueltigeEingabeException("laufzeitende", "LaufzeitendeNachAktuellemBeginn",
					"End of term %s is before the last resumption.".formatted(aenderung.laufzeitende()));
		}
	}

	/** Alle Verträge des Haushalts in der {@link #REIHENFOLGE} der Vertragsliste; ohne Paging (überschaubar viele). */
	@Transactional(readOnly = true)
	List<VertragAntwort> auflisten() {
		return auflisten(berechnung());
	}

	private List<VertragAntwort> auflisten(Vertragsberechnung berechnung) {
		return repository.findAll().stream()
				.map(vertrag -> VertragAntwort.aus(vertrag, berechnung))
				.sorted(REIHENFOLGE)
				.toList();
	}

	/** Kennzahlen für Startseite und Vertragsseite; passt zur {@link #auflisten Liste}, weil es sie auswertet. */
	@Transactional(readOnly = true)
	VertragsuebersichtAntwort uebersicht() {
		// Ein Stichtag für Liste und Jahr, auch um Mitternacht an Silvester.
		var berechnung = berechnung();
		return VertragsuebersichtAntwort.aus(auflisten(berechnung), berechnung.heute().getYear());
	}

	/**
	 * Löscht den Vertrag endgültig.
	 *
	 * @throws NichtGefundenException wie bei {@link #abrufen}, auch wenn er schon gelöscht ist
	 * @throws KonfliktException wenn {@code version} veraltet ist; dann bleibt der Vertrag
	 */
	@Transactional
	void loeschen(UUID id, long version) {
		var vertrag = vertrag(id);
		vertrag.pruefeVersion(version);
		repository.delete(vertrag);
	}

	/** Wie in der SPA: die Bezeichnung, ohne sie der Anbieter. */
	private static String anzeigeName(VertragAntwort vertrag) {
		return vertrag.bezeichnung() != null ? vertrag.bezeichnung() : vertrag.anbieter();
	}

	private Vertrag vertrag(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new NichtGefundenException("Contract %s not found.".formatted(id)));
	}

	private VertragKategorie kategorie(UUID id) {
		return kategorieService.verwendbare(id).orElseThrow(() -> new UngueltigeEingabeException("kategorieId",
				"Unbekannt", "Contract category %s not found.".formatted(id)));
	}

	private Vertragsberechnung berechnung() {
		return new Vertragsberechnung(LocalDate.now(clock));
	}

}
