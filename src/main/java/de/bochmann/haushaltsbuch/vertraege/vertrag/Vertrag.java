package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fachdaten.FachEntity;
import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Vertrag eines Haushalts, z.&nbsp;B. ein Abo oder eine Versicherung. Gespeichert werden nur die
 * Angaben des Nutzers; Status, Kosten, nächste Abbuchung und Kündigung berechnet
 * {@link de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung} beim Lesen.
 * <p>
 * Angaben, die nicht zum Vertrag passen, leert {@link #setzen}: Ein nicht kündbarer Vertrag hat weder
 * Verlängerung noch Kündigungsfrist; leere Texte werden {@code null}.
 */
@Entity
@Table(name = "vertrag")
class Vertrag extends FachEntity {

	@Column(name = "anbieter", nullable = false, length = 200)
	private String anbieter;

	@Column(name = "bezeichnung", length = 200)
	private String bezeichnung;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "kategorie_id")
	private VertragKategorie kategorie;

	@Column(name = "vertragsnummer", length = 100)
	private String vertragsnummer;

	@Embedded
	private Betrag betrag;

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlweise", nullable = false, length = 40)
	private Zahlweise zahlweise;

	@Column(name = "abbuchung_ab", nullable = false)
	private LocalDate abbuchungAb;

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlungsweg", length = 40)
	private Zahlungsweg zahlungsweg;

	@Column(name = "kuendbar", nullable = false)
	private boolean kuendbar;

	@Column(name = "beginn")
	private LocalDate beginn;

	@Column(name = "laufzeitende")
	private LocalDate laufzeitende;

	@Enumerated(EnumType.STRING)
	@Column(name = "verlaengerung", length = 40)
	private Verlaengerung verlaengerung;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "kuendigungsfrist_monate")
	private Integer kuendigungsfristMonate;

	@Column(name = "gekuendigt_zum")
	private LocalDate gekuendigtZum;

	@Column(name = "notiz", length = 1000)
	private String notiz;

	protected Vertrag() {
	}

	Vertrag(VertragAnfrage anfrage, VertragKategorie kategorie) {
		setzen(anfrage, kategorie);
	}

	/** Übernimmt alle Angaben der Anfrage (Anlegen, später auch Ändern mit {@code PUT}). */
	void setzen(VertragAnfrage anfrage, VertragKategorie kategorie) {
		this.anbieter = anfrage.anbieter().strip();
		this.bezeichnung = text(anfrage.bezeichnung());
		this.kategorie = kategorie;
		this.vertragsnummer = text(anfrage.vertragsnummer());
		this.betrag = anfrage.betrag();
		this.zahlweise = anfrage.zahlweise();
		this.abbuchungAb = anfrage.abbuchungAb();
		this.zahlungsweg = anfrage.zahlungsweg();
		this.kuendbar = anfrage.kuendbar();
		this.beginn = anfrage.beginn();
		this.laufzeitende = anfrage.laufzeitende();
		this.verlaengerung = kuendbar ? anfrage.verlaengerung() : null;
		this.kuendigungsfristMonate = kuendbar ? anfrage.kuendigungsfristMonate() : null;
		this.gekuendigtZum = anfrage.gekuendigtZum();
		this.notiz = text(anfrage.notiz());
	}

	private static String text(String wert) {
		return wert == null || wert.isBlank() ? null : wert.strip();
	}

	/** Die Angaben für die Berechnung. */
	Vertragsdaten daten() {
		return new Vertragsdaten(zahlweise, abbuchungAb, kuendbar, beginn, laufzeitende, verlaengerung,
				kuendigungsfristMonate, gekuendigtZum);
	}

	String getAnbieter() {
		return anbieter;
	}

	String getBezeichnung() {
		return bezeichnung;
	}

	VertragKategorie getKategorie() {
		return kategorie;
	}

	String getVertragsnummer() {
		return vertragsnummer;
	}

	Betrag getBetrag() {
		return betrag;
	}

	Zahlweise getZahlweise() {
		return zahlweise;
	}

	LocalDate getAbbuchungAb() {
		return abbuchungAb;
	}

	Zahlungsweg getZahlungsweg() {
		return zahlungsweg;
	}

	boolean isKuendbar() {
		return kuendbar;
	}

	LocalDate getBeginn() {
		return beginn;
	}

	LocalDate getLaufzeitende() {
		return laufzeitende;
	}

	Verlaengerung getVerlaengerung() {
		return verlaengerung;
	}

	Integer getKuendigungsfristMonate() {
		return kuendigungsfristMonate;
	}

	LocalDate getGekuendigtZum() {
		return gekuendigtZum;
	}

	String getNotiz() {
		return notiz;
	}

}
