package de.bochmann.haushaltsbuch.vertraege.vertrag;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** Verträge des eigenen Haushalts (Komponente {@code VERTRAGSUEBERSICHT}, siehe {@code SecurityConfig}). */
@RestController
@Tag(name = "Verträge")
@RequestMapping("/api/vertraege")
class VertragController {

	private final VertragService service;

	VertragController(VertragService service) {
		this.service = service;
	}

	/** Sortiert nach Dringlichkeit der Kündigung, siehe {@code docs/api.md}. */
	@GetMapping
	List<VertragAntwort> vertraegeAuflisten() {
		return service.auflisten();
	}

	/** Laufende Verträge, Kosten je Währung und die nächsten Kündigungsfristen, siehe {@code docs/api.md}. */
	@GetMapping("/uebersicht")
	VertragsuebersichtAntwort vertragsuebersichtAbrufen() {
		return service.uebersicht();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // für die OpenAPI-Beschreibung; den Status setzt ResponseEntity
	ResponseEntity<VertragAntwort> vertragAnlegen(@Valid @RequestBody VertragAnfrage anfrage) {
		VertragAntwort vertrag = service.anlegen(anfrage);
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(vertrag.id()).toUri();
		return ResponseEntity.created(location).body(vertrag);
	}

	/** Kündigungstermin für das Formular, ohne zu speichern; daher 200 statt 201. */
	@PostMapping("/vorschau")
	VertragVorschauAntwort vertragVorschauBerechnen(@Valid @RequestBody VertragVorschauAnfrage anfrage) {
		return service.vorschau(anfrage);
	}

	/** Unbekannte ID oder Vertrag eines anderen Haushalts: 404. */
	@GetMapping("/{id}")
	VertragAntwort vertragAbrufen(@PathVariable UUID id) {
		return service.abrufen(id);
	}

	/** Ersetzt alle Angaben; veraltete {@code version}: 409, unbekannte ID oder fremder Vertrag: 404. */
	@PutMapping("/{id}")
	VertragAntwort vertragAendern(@PathVariable UUID id, @Valid @RequestBody VertragAenderung aenderung) {
		return service.aendern(id, aenderung);
	}

	/**
	 * Löscht endgültig; {@code version} wie beim Ändern, damit keine Änderung eines anderen Mitglieds
	 * unbemerkt mit verschwindet (veraltet: 409). Unbekannte ID oder fremder Vertrag: 404.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void vertragLoeschen(@PathVariable UUID id, @RequestParam long version) {
		service.loeschen(id, version);
	}

}
