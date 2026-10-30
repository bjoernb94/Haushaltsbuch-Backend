package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.Komponente;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nutzer der Anwendung. Die ID wird nicht generiert, sondern von Keycloak übernommen.
 * <p>
 * Implementiert {@link Persistable}, damit {@code save} einen neuen Nutzer per {@code persist}
 * anlegt statt per {@code merge}: Eine bereits vergebene ID führt so zu einem Fehler, statt
 * einen bestehenden Nutzer still zu überschreiben.
 */
@Entity
@Table(name = "nutzer")
class Nutzer implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "angelegt_am", nullable = false)
	private Instant angelegtAm;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "nutzer_komponente", joinColumns = @JoinColumn(name = "nutzer_id"))
	@Column(name = "komponente", nullable = false)
	@Enumerated(EnumType.STRING)
	private Set<Komponente> komponenten = EnumSet.noneOf(Komponente.class);

	@Transient
	private boolean neu = true;

	protected Nutzer() {
	}

	Nutzer(UUID id, Set<Komponente> komponenten, Instant angelegtAm) {
		this.id = id;
		this.komponenten = EnumSet.copyOf(komponenten);
		this.angelegtAm = angelegtAm;
	}

	@Override
	public UUID getId() {
		return id;
	}

	Instant getAngelegtAm() {
		return angelegtAm;
	}

	Set<Komponente> getKomponenten() {
		return Set.copyOf(komponenten);
	}

	@Override
	public boolean isNew() {
		return neu;
	}

	@PostLoad
	@PostPersist
	void markiereGespeichert() {
		neu = false;
	}

}
