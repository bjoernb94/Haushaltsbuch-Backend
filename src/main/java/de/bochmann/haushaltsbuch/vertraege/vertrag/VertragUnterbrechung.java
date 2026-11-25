package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Eine Unterbrechung des Vertrags, als Zeile in {@code vertrag_unterbrechung} gespeichert. Entsteht nur bei der
 * Wiederaufnahme und lässt sich danach nur entfernen, nicht ändern (ADR 0023). In der API
 * {@link VertragUnterbrechungAntwort}.
 *
 * @param id für {@code unterbrechungIds} beim Ändern
 */
@Embeddable
record VertragUnterbrechung(
		@Column(name = "id")
		UUID id,
		@Column(name = "von")
		LocalDate von,
		@Column(name = "bis")
		LocalDate bis,
		@Enumerated(EnumType.STRING)
		@Column(name = "art", length = 40)
		Unterbrechungsart art) {

	/** Eine neue Unterbrechung mit eigener ID. */
	static VertragUnterbrechung neu(LocalDate von, LocalDate bis, Unterbrechungsart art) {
		return new VertragUnterbrechung(UUID.randomUUID(), von, bis, art);
	}

	Unterbrechung unterbrechung() {
		return new Unterbrechung(von, bis, art);
	}

}
