package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Basisklasse für Fach-Entities (Verträge, Buchungen …): ID, Version und Audit-Spalten.
 * <p>
 * Spalten, die jede Tabelle einer Fach-Entity dafür braucht:
 * <pre>
 * id           UUID PRIMARY KEY,
 * version      BIGINT      NOT NULL,
 * angelegt_am  TIMESTAMPTZ NOT NULL,
 * angelegt_von UUID,
 * geaendert_am TIMESTAMPTZ NOT NULL,
 * geaendert_von UUID
 * </pre>
 * Die Audit-Spalten füllt Spring Data ({@link FachdatenConfig}); {@code *_von} ist die Keycloak-ID
 * des angemeldeten Nutzers und leer, wenn ohne Anmeldung gespeichert wird (z.&nbsp;B. Hintergrundjob).
 * Sie werden gespeichert, aber noch nicht ausgeliefert (Issue #42).
 * <p>
 * {@link #version} schützt vor dem stillen Überschreiben gleichzeitiger Änderungen (optimistic
 * locking): Das DTO liefert die Version aus, {@code PUT} schickt sie zurück, und der Service prüft
 * sie mit {@link #pruefeVersion(long)}, bevor er Felder ändert.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class FachEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@CreatedDate
	@Column(name = "angelegt_am", nullable = false, updatable = false)
	private Instant angelegtAm;

	@CreatedBy
	@Column(name = "angelegt_von", updatable = false)
	private UUID angelegtVon;

	@LastModifiedDate
	@Column(name = "geaendert_am", nullable = false)
	private Instant geaendertAm;

	@LastModifiedBy
	@Column(name = "geaendert_von")
	private UUID geaendertVon;

	public UUID getId() {
		return id;
	}

	public long getVersion() {
		return version;
	}

	public Instant getAngelegtAm() {
		return angelegtAm;
	}

	public UUID getAngelegtVon() {
		return angelegtVon;
	}

	public Instant getGeaendertAm() {
		return geaendertAm;
	}

	public UUID getGeaendertVon() {
		return geaendertVon;
	}

	/**
	 * Prüft die vom Client mitgeschickte Version. Hibernate vergleicht beim Speichern nur mit der
	 * gerade geladenen Version, nicht mit der, die der Client gesehen hat; daher diese Prüfung.
	 *
	 * @throws KonfliktException wenn ein anderer den Datensatz inzwischen geändert hat (409)
	 */
	public void pruefeVersion(long erwartet) {
		if (version != erwartet) {
			throw new KonfliktException("%s %s was modified in the meantime (version %d, expected %d)."
					.formatted(getClass().getSimpleName(), id, version, erwartet));
		}
	}

}
