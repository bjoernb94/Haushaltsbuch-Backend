package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fachdaten.FachEntity;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisprognose;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.kategorie.VertragKategorie;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vertrag eines Haushalts, z.&nbsp;B. ein Abo oder eine Versicherung. Gespeichert werden nur die
 * Angaben des Nutzers, auch die Preise mit Gültigkeitsdatum; aktueller Betrag, Status, Kosten, Vertragsende
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

	/** Spalten {@code preissteigerung_*}; {@code null} ohne Regel (alle Spalten leer). */
	@Embedded
	private VertragPreissteigerung preissteigerung;

	@Column(name = "preisgarantie_bis")
	private LocalDate preisgarantieBis;

	/** Wird in v0.2 nur gespeichert und angezeigt; die Erinnerung selbst kommt mit den zentralen Meldungen. */
	@Column(name = "erinnerung_sonderkuendigung", nullable = false)
	private boolean erinnerungSonderkuendigung;

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlweise", nullable = false, length = 40)
	private Zahlweise zahlweise;

	@Enumerated(EnumType.STRING)
	@Column(name = "zahlungsweg", length = 40)
	private Zahlungsweg zahlungsweg;

	@Column(name = "anteil_prozent", precision = 4, scale = 1)
	private BigDecimal anteilProzent;

	/**
	 * Wie {@link #preise} ohne eigene {@code haushalt_id} und als Ganzes ersetzt. Nachgeladen für alle Verträge einer
	 * Abfrage auf einmal, weil Hibernate nicht zwei Listen zugleich per Join laden kann.
	 */
	@ElementCollection
	@CollectionTable(name = "vertrag_einmalkosten", joinColumns = @JoinColumn(name = "vertrag_id"))
	@AttributeOverride(name = "betrag.wert", column = @Column(name = "betrag_wert"))
	@AttributeOverride(name = "betrag.waehrung", column = @Column(name = "betrag_waehrung"))
	@Fetch(FetchMode.SUBSELECT)
	private List<VertragEinmalkosten> einmalkosten = new ArrayList<>();

	@Column(name = "kuendbar", nullable = false)
	private boolean kuendbar;

	/** Anker des Zahlungsrhythmus. */
	@Column(name = "beginn", nullable = false)
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

	/** Nach Fälligkeit, dann Bezeichnung; der Betrag nur, damit gleiche Tage und Namen fest sortiert sind. */
	private static final Comparator<VertragEinmalkosten> EINMALKOSTEN_REIHENFOLGE = Comparator
			.comparing(VertragEinmalkosten::faelligAm)
			.thenComparing(VertragEinmalkosten::bezeichnung)
			.thenComparing(einmalkosten -> einmalkosten.betrag().wert());

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
		var preise = anfrage.preise().stream().map(VertragPreisAnfrage::vertragPreis).sorted(REIHENFOLGE).toList();
		// Nur bei einer Änderung ersetzen, sonst erhöht Hibernate die Version ohne Grund.
		if (!getPreise().equals(preise)) {
			this.preise.clear();
			this.preise.addAll(preise);
		}
		this.preissteigerung = anfrage.preissteigerung() != null ? anfrage.preissteigerung().preissteigerung() : null;
		this.preisgarantieBis = anfrage.preisgarantieBis();
		this.erinnerungSonderkuendigung = anfrage.erinnerungSonderkuendigung();
		this.zahlweise = anfrage.zahlweise();
		this.zahlungsweg = anfrage.zahlungsweg();
		// Mit fester Nachkommastelle wie aus der Datenbank, damit die Antwort beim Anlegen dieselbe ist (50 → 50.0).
		this.anteilProzent = anfrage.anteilProzent() != null ? anfrage.anteilProzent().setScale(1) : null;
		var einmalkosten = anfrage.einmalkosten().stream().map(VertragEinmalkostenAnfrage::einmalkosten)
				.sorted(EINMALKOSTEN_REIHENFOLGE).toList();
		// Wie bei den Preisen nur bei einer Änderung ersetzen.
		if (!getEinmalkosten().equals(einmalkosten)) {
			this.einmalkosten.clear();
			this.einmalkosten.addAll(einmalkosten);
		}
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
		return new Vertragsdaten(zahlweise, kuendbar, beginn, laufzeitende, verlaengerung,
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

	VertragPreissteigerung getPreissteigerung() {
		return preissteigerung;
	}

	LocalDate getPreisgarantieBis() {
		return preisgarantieBis;
	}

	boolean isErinnerungSonderkuendigung() {
		return erinnerungSonderkuendigung;
	}

	/** Alle Angaben für die Berechnung, siehe {@code Vertragsberechnung#werte}. */
	Berechnungsgrundlage grundlage() {
		return new Berechnungsgrundlage(daten(), preisliste(), preisprognose(), anteil(),
				einmalkosten.stream().map(VertragEinmalkosten::einmalkosten).toList());
	}

	/** Steigerungsregel und Garantie für die Berechnung. */
	Preisprognose preisprognose() {
		return new Preisprognose(preissteigerung != null ? preissteigerung.preissteigerung() : null, preisgarantieBis);
	}

	Zahlweise getZahlweise() {
		return zahlweise;
	}

	Zahlungsweg getZahlungsweg() {
		return zahlungsweg;
	}

	/** Sortiert nach Fälligkeit, dann Bezeichnung. */
	List<VertragEinmalkosten> getEinmalkosten() {
		return einmalkosten.stream().sorted(EINMALKOSTEN_REIHENFOLGE).toList();
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
