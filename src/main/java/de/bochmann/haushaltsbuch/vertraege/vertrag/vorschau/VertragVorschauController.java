package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vorschau des Vertragsformulars (Komponente {@code VERTRAGSUEBERSICHT} wie die übrigen Verträge-Endpunkte). */
@RestController
@Tag(name = "Verträge")
@RequestMapping("/api/vertraege")
class VertragVorschauController {

	private final VertragVorschauService service;

	VertragVorschauController(VertragVorschauService service) {
		this.service = service;
	}

	/** Berechnete Werte für das Formular (Zusammenfassung), ohne zu speichern; daher 200 statt 201. */
	@PostMapping("/vorschau")
	VertragVorschauAntwort vertragVorschauBerechnen(@Valid @RequestBody VertragVorschauAnfrage anfrage) {
		return service.vorschau(anfrage);
	}

}
