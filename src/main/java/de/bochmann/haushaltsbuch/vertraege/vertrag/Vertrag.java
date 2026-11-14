package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fachdaten.FachEntity;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vertrag eines Haushalts, z.&nbsp;B. ein Abo oder eine Versicherung. Gespeichert werden nur die
 * Angaben des Nutzers, auch die Preise mit Gültigkeitsdatum; aktueller Betrag, Status, Kosten, nächste Abbuchung
 * und Kündigung berechnet {@link de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung} beim Lesen.
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

	/**
	 * Gehört zum Vertrag und ist nur über ihn erreichbar, daher ohne eigene {@code haushalt_id}. {@code PUT} ersetzt
	 * die Liste als Ganzes; die {@code version} des Vertrags deckt sie mit ab.
	 */
	@ElementCollection
	@CollectionTable(name = "vertrag_preis", joinColumns = @JoinColumn(name = "vertrag_id"))
	@AttributeOverride(name = "betrag.wert", column = @Column(name = "betrag_wert"))
	@AttributeOverride(name = "betrag.waehrung", column = @Column(name = "betrag_waehrung"))
	private List<VertragPreis> preise = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlweise", nullable = false, length = 40)
	private Zahlweise zahlweise;

	@Column(name = "abbuchung_ab", nullable = false)
	private LocalDate abbuchungAb;

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlungsweg", length = 40)
	private Zahlungsweg zahlungsweg;

	@Column(name = "anteil_prozent", precision = 4, scale = 1)
	private BigDecimal anteilProzent;

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

	private static final Comparator<VertragPreis> REIHENFOLGE = Comparator.comparing(VertragPreis::preis,
			Preisliste.REIHENFOLGE);

	protected Vertrag() {
	}

	Vertrag(Vertragsangaben anfrage, VertragKategorie kategorie) {
		setzen(anfrage, kategorie);
	}

	/** Übernimmt alle Angaben, beim Anlegen wie beim Ändern ({@code PUT} ersetzt alle Felder). */
	void setzen(Vertragsangaben anfrage, VertragKategorie kategorie) {
		this.anbieter = anfrage.anbieter().strip();
		this.bezeichnung = text(anfrage.bezeichnung());
		this.kategorie = kategorie;
		this.vertragsnummer = text(anfrage.vertragsnummer());
		var preise = anfrage.preise().stream().map(VertragPreisAnfrage::preis).sorted(REIHENFOLGE).toList();
		// Nur bei einer Änderung ersetzen, sonst erhöht Hibernate die Version ohne Grund.
		if (!getPreise().equals(preise)) {
			this.preise.clear();
			this.preise.addAll(preise);
		}
		this.zahlweise = anfrage.zahlweise();
		this.abbuchungAb = anfrage.abbuchungAb();
		this.zahlungsweg = anfrage.zahlungsweg();
		// Mit fester Nachkommastelle wie aus der Datenbank, damit die Antwort beim Anlegen dieselbe ist (50 → 50.0).
		this.anteilProzent = anfrage.anteilProzent() != null ? anfrage.anteilProzent().setScale(1) : null;
		this.kuendbar = anfrage.kuendbar();
		this.beginn = anfrage.beginn();
		this.laufzeitende = anfrage.laufzeitende();
		// Bereinigt wie in der Vorschau, siehe Berechnungsangaben#daten.
		var daten = anfrage.daten();
		this.verlaengerung = daten.verlaengerung();
		this.kuendigungsfristMonate = daten.kuendigungsfristMonate();
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

	/** Sortiert nach {@code gueltigAb}, der Eintrag ohne Datum zuerst. */
	List<VertragPreis> getPreise() {
		return preise.stream().sorted(REIHENFOLGE).toList();
	}

	/** Die Preise für die Berechnung. */
	Preisliste preisliste() {
		return new Preisliste(preise.stream().map(VertragPreis::preis).toList());
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

	BigDecimal getAnteilProzent() {
		return anteilProzent;
	}

	/** Der Anteil für die Berechnung; ohne Angabe 100&nbsp;%. */
	Anteil anteil() {
		return Anteil.aus(anteilProzent);
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
