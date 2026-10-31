package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Haushalt, dem die Fachdaten gehören. Mitglieder werden nur über ihre Keycloak-ID
 * (Claim {@code sub}) referenziert, ohne Fremdschlüssel auf das Modul {@code nutzer}.
 */
@Entity
@Table(name = "haushalt")
class Haushalt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Optional, z.&nbsp;B. „Familie Muster“; bisher ohne Endpunkt zum Ändern. */
	@Column(name = "name", length = 100)
	private String name;

	@Column(name = "angelegt_am", nullable = false)
	private Instant angelegtAm;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "haushalt_mitglied", joinColumns = @JoinColumn(name = "haushalt_id"))
	@Column(name = "nutzer_id", nullable = false)
	private Set<UUID> mitglieder = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "haushalt_komponente", joinColumns = @JoinColumn(name = "haushalt_id"))
	@Column(name = "komponente", nullable = false)
	@Enumerated(EnumType.STRING)
	private Set<Komponente> komponenten = EnumSet.noneOf(Komponente.class);

	protected Haushalt() {
	}

	/** Neuer Haushalt mit dem gründenden Nutzer als einzigem Mitglied. */
	Haushalt(UUID gruenderId, Set<Komponente> komponenten, Instant angelegtAm) {
		this.mitglieder.add(gruenderId);
		this.komponenten = EnumSet.copyOf(komponenten);
		this.angelegtAm = angelegtAm;
	}

	UUID getId() {
		return id;
	}

	String getName() {
		return name;
	}

	Instant getAngelegtAm() {
		return angelegtAm;
	}

	Set<UUID> getMitglieder() {
		return Set.copyOf(mitglieder);
	}

	Set<Komponente> getKomponenten() {
		return Set.copyOf(komponenten);
	}

}
