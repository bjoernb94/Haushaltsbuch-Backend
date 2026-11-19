package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Haushalt, dem die Fachdaten gehören. Mitglieder werden nur über ihre Keycloak-ID
 * (Claim {@code sub}) referenziert, ohne Fremdschlüssel auf das Modul {@code nutzer}.
 * <p>
 * Version und Audit-Spalten wie bei einer {@code FachEntity}, aber ohne Tenant: Der Haushalt ist
 * selbst der Tenant. Die Version gilt für den ganzen Haushalt; Hibernate erhöht sie auch, wenn sich
 * nur Mitglieder oder Komponenten ändern. {@code geaendert_*} füllt Spring Data, {@code angelegt_von}
 * ist der Gründer (bei der Registrierung ist noch niemand angemeldet).
 */
@Entity
@Table(name = "haushalt")
@EntityListeners(AuditingEntityListener.class)
class Haushalt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	/** Optional, z.&nbsp;B. „Familie Muster“; bisher ohne Endpunkt zum Ändern. */
	@Column(name = "name", length = 100)
	private String name;

	@Column(name = "angelegt_am", nullable = false)
	private Instant angelegtAm;

	@Column(name = "angelegt_von", updatable = false)
	private UUID angelegtVon;

	@LastModifiedDate
	@Column(name = "geaendert_am", nullable = false)
	private Instant geaendertAm;

	@LastModifiedBy
	@Column(name = "geaendert_von")
	private UUID geaendertVon;

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
		this.angelegtVon = gruenderId;
	}

	UUID getId() {
		return id;
	}

	long getVersion() {
		return version;
	}

	String getName() {
		return name;
	}

	Instant getAngelegtAm() {
		return angelegtAm;
	}

	UUID getAngelegtVon() {
		return angelegtVon;
	}

	Instant getGeaendertAm() {
		return geaendertAm;
	}

	UUID getGeaendertVon() {
		return geaendertVon;
	}

	Set<UUID> getMitglieder() {
		return Set.copyOf(mitglieder);
	}

	Set<Komponente> getKomponenten() {
		return Set.copyOf(komponenten);
	}

	/**
	 * Ersetzt die gebuchten Komponenten. Dieselbe Auswahl lässt den Haushalt unverändert, damit die
	 * Version nicht ohne Änderung steigt (Hibernate hielte die geleerte und neu gefüllte Collection
	 * für geändert).
	 */
	void komponentenSetzen(Set<Komponente> neu) {
		if (neu.isEmpty()) {
			throw new IllegalArgumentException("A household needs at least one component.");
		}
		if (!komponenten.equals(neu)) {
			komponenten.clear();
			komponenten.addAll(neu);
		}
	}

	/**
	 * Prüft die vom Client mitgeschickte Version, wie {@code FachEntity#pruefeVersion}.
	 *
	 * @throws KonfliktException wenn ein anderer den Haushalt inzwischen geändert hat (409)
	 */
	void pruefeVersion(long erwartet) {
		if (version != erwartet) {
			throw new KonfliktException("Haushalt %s was modified in the meantime (version %d, expected %d)."
					.formatted(id, version, erwartet));
		}
	}

}
