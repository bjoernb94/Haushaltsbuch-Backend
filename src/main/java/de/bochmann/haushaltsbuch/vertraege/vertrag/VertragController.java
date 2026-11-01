package de.bochmann.haushaltsbuch.vertraege.vertrag;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Verträge des eigenen Haushalts (Komponente {@code VERTRAGSUEBERSICHT}, siehe {@code SecurityConfig}). */
@RestController
@Tag(name = "Verträge")
@RequestMapping("/api/vertraege")
class VertragController {

	private final VertragService service;

	VertragController(VertragService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED) // für die OpenAPI-Beschreibung; den Status setzt ResponseEntity
	ResponseEntity<VertragAntwort> vertragAnlegen(@Valid @RequestBody VertragAnfrage anfrage) {
		VertragAntwort vertrag = service.anlegen(anfrage);
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(vertrag.id()).toUri();
		return ResponseEntity.created(location).body(vertrag);
	}

}
