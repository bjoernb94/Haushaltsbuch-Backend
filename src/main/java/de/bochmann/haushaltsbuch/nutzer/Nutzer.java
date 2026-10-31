package de.bochmann.haushaltsbuch.nutzer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Nutzer der Anwendung. Die ID wird nicht generiert, sondern von Keycloak übernommen. Gebuchte
 * Komponenten gehören nicht dem Nutzer, sondern seinem Haushalt (Modul {@code haushalt}).
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

	@Transient
	private boolean neu = true;

	protected Nutzer() {
	}

	Nutzer(UUID id, Instant angelegtAm) {
		this.id = id;
		this.angelegtAm = angelegtAm;
	}

	@Override
	public UUID getId() {
		return id;
	}

	Instant getAngelegtAm() {
		return angelegtAm;
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
