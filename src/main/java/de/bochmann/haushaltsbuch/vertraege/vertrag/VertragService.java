package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.uebersicht.Vertragsuebersicht;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieService;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Unterbrechungsregeln;
import de.bochmann.haushaltsbuch.vertraege.vertrag.regeln.Wiederaufnahmeregeln;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
		Unterbrechungsregeln.pruefe(aenderung, vertrag.daten().unterbrechungen());
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
		Wiederaufnahmeregeln.pruefe(wiederaufnahme, vertrag.isKuendbar(), vertrag.preisliste(), vertragsende);
		vertrag.wiederaufnehmen(wiederaufnahme.datum(), vertragsende, wiederaufnahme.laufzeitende(),
				wiederaufnahme.verlaengerung(), wiederaufnahme.kuendigungsfristMonate(), wiederaufnahme.betrag(),
				berechnung);
		repository.flush();
		return VertragAntwort.aus(vertrag, berechnung);
	}

	/** Alle Verträge des Haushalts in der {@link #REIHENFOLGE} der Vertragsliste; ohne Paging (überschaubar viele). */
	@Transactional(readOnly = true)
	List<VertragAntwort> auflisten() {
		return eintraege(berechnung()).stream().map(Vertragsuebersicht.Eintrag::vertrag).toList();
	}

	/** Jeder Vertrag als Antwort mit Grundlage und Werten (einmal berechnet), in der {@link #REIHENFOLGE}. */
	private List<Vertragsuebersicht.Eintrag<VertragAntwort>> eintraege(Vertragsberechnung berechnung) {
		int jahr = berechnung.heute().getYear();
		return repository.findAll().stream()
				.map(vertrag -> {
					var grundlage = vertrag.grundlage();
					var werte = berechnung.werte(grundlage);
					return new Vertragsuebersicht.Eintrag<>(VertragAntwort.aus(vertrag, werte, jahr), grundlage, werte);
				})
				.sorted(Comparator.comparing(Vertragsuebersicht.Eintrag::vertrag, REIHENFOLGE))
				.toList();
	}

	/** Kennzahlen für Startseite und Vertragsseite; passt zur {@link #auflisten Liste}, weil es sie auswertet. */
	@Transactional(readOnly = true)
	VertragsuebersichtAntwort uebersicht() {
		// Ein Stichtag für Liste und Jahr, auch um Mitternacht an Silvester.
		var berechnung = berechnung();
		int jahr = berechnung.heute().getYear();
		return VertragsuebersichtAntwort.aus(Vertragsuebersicht.aus(eintraege(berechnung), jahr), jahr);
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
