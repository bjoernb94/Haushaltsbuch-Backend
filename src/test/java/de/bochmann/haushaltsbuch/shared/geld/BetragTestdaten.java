package de.bochmann.haushaltsbuch.shared.geld;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Entity nur für {@link BetragIntegrationTests}; die Tabelle legt die Test-Migration
 * {@code V1000__test_betrag.sql} an. Zwei Beträge prüfen das Namensschema der Spalten.
 */
@Entity
@Table(name = "test_betrag")
class BetragTestdaten {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	UUID id;

	Betrag preis;

	Betrag gebuehr;

	protected BetragTestdaten() {
	}

	BetragTestdaten(Betrag preis, Betrag gebuehr) {
		this.preis = preis;
		this.gebuehr = gebuehr;
	}

}
