package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorieService;
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
	 *
	 * @throws NichtGefundenException wie bei {@link #abrufen}
	 * @throws KonfliktException wenn {@code version} veraltet ist, also inzwischen jemand anderes geändert hat
	 * @throws UngueltigeEingabeException wie bei {@link #anlegen}
	 */
	@Transactional
	VertragAntwort aendern(UUID id, VertragAenderung aenderung) {
		var vertrag = vertrag(id);
		vertrag.pruefeVersion(aenderung.version());
		var berechnung = berechnung();
		vertrag.setzen(aenderung, kategorie(aenderung.kategorieId()), berechnung);
		// Hibernate erhöht die Version erst beim Schreiben; die Antwort braucht schon die neue.
		repository.flush();
		return VertragAntwort.aus(vertrag, berechnung);
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
	 * Die berechneten Werte zu den Formularwerten, ohne zu speichern; dieselbe Berechnung wie für gespeicherte
	 * Verträge, mit den Preisen, wie sie nach dem Speichern wären (samt den Stufen, die das Speichern anlegt).
	 */
	VertragVorschauAntwort vorschau(VertragVorschauAnfrage anfrage) {
		var berechnung = berechnung();
		var grundlage = berechnung.wieGespeichert(anfrage.grundlage());
		return VertragVorschauAntwort.aus(berechnung.werte(grundlage), grundlage, anfrage);
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
