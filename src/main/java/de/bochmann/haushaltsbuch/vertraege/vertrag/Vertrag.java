package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.fachdaten.FachEntity;
import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Berechnungsgrundlage;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.kosten.Anteil;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisliste;
import de.bochmann.haushaltsbuch.vertraege.berechnung.preis.Preisprognose;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Vertrag eines Haushalts, z.&nbsp;B. ein Abo oder eine Versicherung. Gespeichert werden nur die
 * Angaben des Nutzers, auch die Preise mit Gültigkeitsdatum; aktueller Betrag, Status, Kosten, Vertragsende
 * und Kündigung berechnet {@link Vertragsberechnung} beim Lesen. Ausnahme: Beim Speichern legt {@link #setzen} die
 * fälligen Stufen der Preissteigerung als automatische Preise an (ADR 0022).
 * <p>
 * Unterbrechungen (ADR 0023) entstehen nur bei der {@link #wiederaufnehmen Wiederaufnahme}; beim Ändern lassen sie
 * sich nur entfernen ({@link #unterbrechungenBehalten}).
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

	/**
	 * Bis zu diesem Tag hat das Speichern die Termine der Preissteigerung betrachtet und fällige Stufen angelegt; ein
	 * Termin bis dahin entsteht nie wieder, auch wenn der Nutzer seinen Preis gelöscht hat (#162). Wird nur mit Regel
	 * gesetzt und nur nach hinten verschoben; {@code null}, wenn noch nie mit Regel gespeichert.
	 */
	@Column(name = "preissteigerung_betrachtet_bis")
	private LocalDate preissteigerungBetrachtetBis;

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

	/**
	 * Wie {@link #preise} ohne eigene {@code haushalt_id}; die {@code version} des Vertrags deckt sie mit ab.
	 * Nachgeladen wie {@link #einmalkosten}.
	 */
	@ElementCollection
	@CollectionTable(name = "vertrag_unterbrechung", joinColumns = @JoinColumn(name = "vertrag_id"))
	@Fetch(FetchMode.SUBSELECT)
	private List<VertragUnterbrechung> unterbrechungen = new ArrayList<>();

	private static final Comparator<VertragUnterbrechung> UNTERBRECHUNG_REIHENFOLGE = Comparator
			.comparing(VertragUnterbrechung::von);

	private static final Comparator<VertragPreis> REIHENFOLGE = Comparator.comparing(VertragPreis::preis,
			Preisliste.REIHENFOLGE);

	/** Nach Fälligkeit, dann Bezeichnung; der Betrag nur, damit gleiche Tage und Namen fest sortiert sind. */
	private static final Comparator<VertragEinmalkosten> EINMALKOSTEN_REIHENFOLGE = Comparator
			.comparing(VertragEinmalkosten::faelligAm)
			.thenComparing(VertragEinmalkosten::bezeichnung)
			.thenComparing(einmalkosten -> einmalkosten.betrag().wert());

	protected Vertrag() {
	}

	Vertrag(Vertragsangaben anfrage, VertragKategorie kategorie, Vertragsberechnung berechnung) {
		setzen(anfrage, kategorie, berechnung);
	}

	/**
	 * Übernimmt alle Angaben, beim Anlegen wie beim Ändern ({@code PUT} ersetzt alle Felder), und legt die bis heute
	 * fälligen Stufen der Preissteigerung als automatische Preise an
	 * ({@link Vertragsberechnung#faelligeStufen}). Eine geänderte Regel wirkt so nur nach vorn: Gespeicherte Stufen
	 * bleiben, wie sie sind, und Termine bis {@link #preissteigerungBetrachtetBis} entstehen nicht neu.
	 */
	void setzen(Vertragsangaben anfrage, VertragKategorie kategorie, Vertragsberechnung berechnung) {
		this.anbieter = anfrage.anbieter().strip();
		this.bezeichnung = text(anfrage.bezeichnung());
		this.kategorie = kategorie;
		this.vertragsnummer = text(anfrage.vertragsnummer());
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
		// Zuletzt, weil die fälligen Stufen von Regel, Garantie und Vertragsende abhängen.
		var bisher = getPreise();
		preiseSpeichern(anfrage.preise().stream().map(preis -> preis.vertragPreis(bisher)).toList(), berechnung);
	}

	/**
	 * Behält nur die Unterbrechungen mit diesen IDs (beim Ändern, vor {@link #setzen}); die übrigen fallen weg, und
	 * der Vertrag zählt dort als durchgehend gelaufen.
	 *
	 * @return die IDs, die der Vertrag nicht hat (unbekannt oder von einem anderen Vertrag)
	 */
	List<UUID> unterbrechungenBehalten(Collection<UUID> ids) {
		var unbekannt = ids.stream()
				.filter(id -> unterbrechungen.stream().noneMatch(unterbrechung -> unterbrechung.id().equals(id)))
				.toList();
		if (unbekannt.isEmpty()) {
			// Nur bei einer Änderung, sonst erhöht Hibernate die Version ohne Grund.
			unterbrechungen.removeIf(unterbrechung -> !ids.contains(unterbrechung.id()));
		}
		return unbekannt;
	}

	/**
	 * Nimmt den gekündigten oder beendeten Vertrag am {@code datum} wieder auf (ADR 0023): Die Zeit nach dem
	 * {@code vertragsende} bis davor wird eine Unterbrechung (ohne Lücke keine), die Kündigung fällt weg, Laufzeit und
	 * Verlängerung gelten ab dann neu, ein neuer Betrag wird ein eingetragener Preis ab {@code datum} und ersetzt eine
	 * automatische Stufe an diesem Tag. Fällige Stufen legt es an wie {@link #setzen}. Geprüft hat der Service.
	 *
	 * @param vertragsende der letzte Tag vor der Wiederaufnahme, siehe {@link Vertragsberechnung#vertragsende}
	 * @param laufzeitende wie beim Ändern auch bei nicht kündbaren Verträgen gespeichert
	 * @param verlaengerung bei nicht kündbaren Verträgen ignoriert
	 * @param kuendigungsfristMonate bei nicht kündbaren Verträgen ignoriert
	 * @param betrag leer, wenn sich der Preis nicht ändert
	 */
	void wiederaufnehmen(LocalDate datum, LocalDate vertragsende, LocalDate laufzeitende, Verlaengerung verlaengerung,
			Integer kuendigungsfristMonate, Betrag betrag, Vertragsberechnung berechnung) {
		// Die bis zum Vertragsende fälligen Stufen zuerst, damit der neue Betrag sie nicht überdeckt.
		var preise = new ArrayList<>(mitFaelligenStufen(getPreise(), berechnung));
		if (vertragsende.plusDays(1).isBefore(datum)) {
			unterbrechungen.add(VertragUnterbrechung.neu(vertragsende.plusDays(1), datum.minusDays(1),
					Unterbrechungsart.KUENDIGUNG));
		}
		this.gekuendigtZum = null;
		this.laufzeitende = laufzeitende;
		this.verlaengerung = kuendbar ? verlaengerung : null;
		this.kuendigungsfristMonate = kuendbar ? kuendigungsfristMonate : null;
		if (betrag != null) {
			preise.removeIf(preis -> preis.automatisch() && preis.gueltigAb().equals(datum));
			preise.add(new VertragPreis(datum, betrag, false));
		}
		preiseSpeichern(preise, berechnung);
	}

	/**
	 * Speichert die Preise samt den bis heute fälligen Stufen ({@link Vertragsberechnung#faelligeStufen}) und merkt
	 * sich, bis wann die Termine betrachtet sind.
	 */
	private void preiseSpeichern(List<VertragPreis> angefragt, Vertragsberechnung berechnung) {
		var bisher = getPreise();
		var preise = mitFaelligenStufen(angefragt, berechnung);
		if (preissteigerung != null) {
			this.preissteigerungBetrachtetBis = preisprognose().betrachtetBis(berechnung.preise().faelligBis(daten()))
					.betrachtetBis();
		}
		// Nur bei einer Änderung ersetzen, sonst erhöht Hibernate die Version ohne Grund.
		if (!bisher.equals(preise)) {
			this.preise.clear();
			this.preise.addAll(preise);
		}
	}

	/** Die Preise ergänzt um die fälligen Stufen, sortiert. */
	private List<VertragPreis> mitFaelligenStufen(List<VertragPreis> preise, Vertragsberechnung berechnung) {
		var stufen = berechnung.preise().faelligeStufen(daten(), new Preisliste(preise.stream().map(VertragPreis::preis).toList()),
				preisprognose());
		return Stream.concat(preise.stream(), stufen.stream().map(VertragPreis::stufe)).sorted(REIHENFOLGE).toList();
	}

	private static String text(String wert) {
		return wert == null || wert.isBlank() ? null : wert.strip();
	}

	/** Die Angaben für die Berechnung. */
	Vertragsdaten daten() {
		return new Vertragsdaten(zahlweise, kuendbar, beginn, laufzeitende, verlaengerung,
				kuendigungsfristMonate, gekuendigtZum,
				unterbrechungen.stream().map(VertragUnterbrechung::unterbrechung).toList());
	}

	/** Sortiert nach {@code von}. */
	List<VertragUnterbrechung> getUnterbrechungen() {
		return unterbrechungen.stream().sorted(UNTERBRECHUNG_REIHENFOLGE).toList();
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

	/** Sortiert nach {@code gueltigAb}. */
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

	LocalDate getPreissteigerungBetrachtetBis() {
		return preissteigerungBetrachtetBis;
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
		return new Preisprognose(preissteigerung != null ? preissteigerung.preissteigerung() : null, preisgarantieBis,
				preissteigerungBetrachtetBis);
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
