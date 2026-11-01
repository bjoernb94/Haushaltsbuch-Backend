package de.bochmann.haushaltsbuch.vertraege.vertrag;

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
		var vertrag = repository.save(new Vertrag(anfrage, kategorie(anfrage.kategorieId())));
		return VertragAntwort.aus(vertrag, berechnung());
	}

	/**
	 * @throws NichtGefundenException wenn es den Vertrag nicht gibt oder er einem anderen Haushalt gehört;
	 *         beides ist bewusst nicht zu unterscheiden
	 */
	@Transactional(readOnly = true)
	VertragAntwort abrufen(UUID id) {
		var vertrag = repository.findById(id)
				.orElseThrow(() -> new NichtGefundenException("Contract %s not found.".formatted(id)));
		return VertragAntwort.aus(vertrag, berechnung());
	}

	/** Alle Verträge des Haushalts in der {@link #REIHENFOLGE} der Vertragsliste; ohne Paging (überschaubar viele). */
	@Transactional(readOnly = true)
	List<VertragAntwort> auflisten() {
		var berechnung = berechnung();
		return repository.findAll().stream()
				.map(vertrag -> VertragAntwort.aus(vertrag, berechnung))
				.sorted(REIHENFOLGE)
				.toList();
	}

	/** Wie in der SPA: die Bezeichnung, ohne sie der Anbieter. */
	private static String anzeigeName(VertragAntwort vertrag) {
		return vertrag.bezeichnung() != null ? vertrag.bezeichnung() : vertrag.anbieter();
	}

	private VertragKategorie kategorie(UUID id) {
		return kategorieService.verwendbare(id).orElseThrow(() -> new UngueltigeEingabeException("kategorieId",
				"Unbekannt", "Contract category %s not found.".formatted(id)));
	}

	private Vertragsberechnung berechnung() {
		return new Vertragsberechnung(LocalDate.now(clock));
	}

}
