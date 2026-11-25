package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Laufzeitberechnung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Vertragsdaten;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Aktueller Preis, Preiszeilen, fällige Stufen der Preissteigerung, Prognose und Preisverlauf eines Vertrags zum
 * Stichtag der {@link Laufzeitberechnung}; Teil der
 * {@link de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung Vertragsberechnung}. Das Vertragsende und
 * der Status kommen aus der Laufzeitberechnung.
 */
public class Preisberechnung {

	/** So viele Preise zeigt {@link #prognose}. */
	public static final int PROGNOSE_PREISE = 3;

	private final Laufzeitberechnung laufzeit;

	private final LocalDate heute;

	public Preisberechnung(Laufzeitberechnung laufzeit) {
		this.laufzeit = laufzeit;
		this.heute = laufzeit.heute();
	}

	/**
	 * Der aktuelle Preis: der heute gültige, bei einem beendeten Vertrag der an seinem letzten Tag gültige (ein
	 * Eintrag nach dem Ende wurde nie gezahlt); beginnt der Vertrag erst in der Zukunft, der erste. Ist seit dem
	 * letzten Speichern eine Stufe der Preissteigerung fällig geworden, ist es diese (siehe {@link #mitErrechneten}).
	 */
	public Betrag aktuellerPreis(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		return aktuellerEintrag(vertrag, preise, prognose).betrag();
	}

	/** Der Eintrag von {@link #aktuellerPreis}, mit seiner Herkunft. */
	public Preis aktuellerEintrag(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		return mitErrechneten(vertrag, preise, prognose).eintragAm(preisstichtag(vertrag));
	}

	/**
	 * Die fälligen Stufen der Preissteigerung, die beim Speichern als automatische Preise angelegt werden: ab dem
	 * letzten Eintrag bis heute, bei einem beendeten Vertrag bis zu seinem letzten Tag (siehe
	 * {@link Preisprognose#faellige}).
	 */
	public List<Preis> faelligeStufen(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		return prognose.faellige(preise, preisstichtag(vertrag), Preisherkunft.AUTOMATISCH);
	}

	/**
	 * Bis zu diesem Tag betrachtet das Speichern die Termine der Preissteigerung ({@link #faelligeStufen}): heute, bei
	 * einem beendeten Vertrag sein letzter Tag. Gespeichert als {@link Preisprognose#betrachtetBis}.
	 */
	public LocalDate faelligBis(Vertragsdaten vertrag) {
		return preisstichtag(vertrag);
	}

	/**
	 * Die gespeicherte Preisliste ergänzt um die Stufen, die seit dem letzten Speichern fällig geworden sind
	 * ({@link Preisherkunft#ERRECHNET}). Beim Speichern legt das Backend sie an, bis dahin rechnen aktueller Preis,
	 * Kosten, Preisverlauf und Prognose mit ihnen.
	 */
	private Preisliste mitErrechneten(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		return preise.mit(prognose.faellige(preise, preisstichtag(vertrag), Preisherkunft.ERRECHNET));
	}

	/**
	 * Die gespeicherten Einträge der Preisliste, sortiert, mit ihrer Art: {@link Preiszeilenart#AKTUELL} ist der heute
	 * (bzw. am letzten Tag) gültige, auch wenn {@link #aktuellerPreis} eine errechnete Stufe darauf ist; davor {@link Preiszeilenart#FRUEHER}, danach {@link Preiszeilenart#ANGEKUENDIGT} oder,
	 * ab einem Tag nach dem Vertragsende, {@link Preiszeilenart#NACH_ENDE} (am letzten Tag gilt ein Preis noch).
	 */
	public List<Preiszeile> preiszeilen(Vertragsdaten vertrag, Preisliste preise) {
		var liste = preise.preise();
		int aktuell = preise.index(preisstichtag(vertrag));
		Optional<LocalDate> ende = laufzeit.vertragsende(vertrag);
		return IntStream.range(0, liste.size())
				.mapToObj(i -> {
					Preis preis = liste.get(i);
					Preiszeilenart art = i == aktuell ? Preiszeilenart.AKTUELL
							: i < aktuell ? Preiszeilenart.FRUEHER
							: ende.isPresent() && preis.gueltigAb().isAfter(ende.get()) ? Preiszeilenart.NACH_ENDE
							: Preiszeilenart.ANGEKUENDIGT;
					return new Preiszeile(preis.gueltigAb(), preis.betrag(), art,
							preis.herkunft() == Preisherkunft.AUTOMATISCH);
				})
				.toList();
	}

	/**
	 * Der Tag, dessen Preis {@link #aktuellerPreis} ist: heute, bei einem beendeten Vertrag sein letzter Tag. Bis
	 * dahin sind die Stufen der Preissteigerung fällig.
	 */
	private LocalDate preisstichtag(Vertragsdaten vertrag) {
		return laufzeit.vertragsende(vertrag).filter(ende -> ende.isBefore(heute)).orElse(heute);
	}

	/**
	 * Die nächste angekündigte Änderung (siehe {@link Preisliste#naechsteAenderung}), sonst der erste Preis der
	 * Prognose; leer bei einem beendeten oder stornierten Vertrag. Die Prognose beginnt erst nach dem letzten Eintrag, ein
	 * angekündigter Preis kommt also immer zuerst. Eine errechnete Stufe bis heute ist keine Änderung, sondern schon
	 * der aktuelle Preis.
	 */
	public Optional<Preisaenderung> naechstePreisaenderung(Vertragsdaten vertrag, Preisliste preise,
			Preisprognose prognose) {
		if (laufzeit.vorbei(vertrag)) {
			return Optional.empty();
		}
		var ende = laufzeit.vertragsende(vertrag);
		return preise.naechsteAenderung(heute, ende)
				.or(() -> prognose.preise(mitErrechneten(vertrag, preise, prognose), heute, ende, LocalDate.MAX, 1)
						.stream()
						.findFirst()
						.map(preis -> new Preisaenderung(preis.gueltigAb(), preis.betrag(), Preisaenderungsart.PROGNOSE)));
	}

	/**
	 * Die nächsten {@value #PROGNOSE_PREISE} Preise der {@link Preisprognose}; leer ohne Regel und bei einem beendeten
	 * oder stornierten Vertrag. Das Vertragsende allein reicht nicht: Bei einer Stornierung in der Zukunft lägen
	 * Termine zwischen heute und dem Kündigungsdatum.
	 */
	public List<Prognosepreis> prognose(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		if (laufzeit.vorbei(vertrag)) {
			return List.of();
		}
		return prognose.preise(mitErrechneten(vertrag, preise, prognose), heute, laufzeit.vertragsende(vertrag),
						LocalDate.MAX, PROGNOSE_PREISE).stream()
				.map(preis -> new Prognosepreis(preis.gueltigAb(), preis.betrag()))
				.toList();
	}

	/**
	 * Die Punkte für das Diagramm des Preisverlaufs, sortiert: die {@link #preiszeilen} ({@link Preiszeilenart#FRUEHER}
	 * und {@link Preiszeilenart#AKTUELL} als {@link Preispunktart#GEZAHLT}, {@link Preiszeilenart#NACH_ENDE} fallen
	 * weg), die seit dem letzten Speichern fälligen Stufen ({@link Preispunktart#ERRECHNET}) und die {@link #prognose}.
	 */
	public List<Preispunkt> preisverlauf(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose) {
		List<Preispunkt> punkte = new ArrayList<>();
		for (Preiszeile zeile : preiszeilen(vertrag, preise)) {
			switch (zeile.art()) {
				case FRUEHER, AKTUELL -> punkte.add(new Preispunkt(zeile.gueltigAb(), zeile.betrag(), Preispunktart.GEZAHLT));
				case ANGEKUENDIGT -> punkte.add(new Preispunkt(zeile.gueltigAb(), zeile.betrag(), Preispunktart.ANGEKUENDIGT));
				case NACH_ENDE -> {
				}
			}
		}
		prognose.faellige(preise, preisstichtag(vertrag), Preisherkunft.ERRECHNET)
				.forEach(preis -> punkte.add(new Preispunkt(preis.gueltigAb(), preis.betrag(), Preispunktart.ERRECHNET)));
		prognose(vertrag, preise, prognose)
				.forEach(preis -> punkte.add(new Preispunkt(preis.ab(), preis.betrag(), Preispunktart.PROGNOSE)));
		punkte.sort(Comparator.comparing(Preispunkt::ab));
		return punkte;
	}

	/**
	 * Die Preisliste ergänzt um die errechneten Stufen und die Prognose bis einschließlich {@code bis}: alle Termine,
	 * nicht nur die {@value #PROGNOSE_PREISE} angezeigten. Für Summen wie die Kosten im Jahr.
	 */
	public Preisliste mitPrognose(Vertragsdaten vertrag, Preisliste preise, Preisprognose prognose, LocalDate bis) {
		var ergaenzt = mitErrechneten(vertrag, preise, prognose);
		return ergaenzt.mit(prognose.preise(ergaenzt, heute, laufzeit.vertragsende(vertrag), bis, Integer.MAX_VALUE));
	}

}
