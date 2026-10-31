package de.bochmann.haushaltsbuch.shared.fachdaten;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Entity nur für {@link FachEntityTests}; Tabelle aus der Test-Migration {@code V1001__test_fach_entity.sql}. */
@Entity
@Table(name = "test_fach_entity")
class FachEntityTestdaten extends FachEntity {

	@Column(name = "text", nullable = false)
	String text;

	protected FachEntityTestdaten() {
	}

	FachEntityTestdaten(String text) {
		this.text = text;
	}

}
