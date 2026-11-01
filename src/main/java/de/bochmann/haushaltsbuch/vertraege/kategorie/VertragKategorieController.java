package de.bochmann.haushaltsbuch.vertraege.kategorie;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Auswahlliste „Kategorie“ im Vertragsformular. */
@RestController
@Tag(name = "Verträge")
@RequestMapping("/api/vertraege/kategorien")
class VertragKategorieController {

	private final VertragKategorieService service;

	VertragKategorieController(VertragKategorieService service) {
		this.service = service;
	}

	/** In fester Reihenfolge, Standardkategorien und (ab #34) die eigenen des Haushalts. */
	@GetMapping
	List<VertragKategorieAntwort> vertragskategorienAbrufen() {
		return service.verwendbare().stream().map(VertragKategorieAntwort::aus).toList();
	}

}
