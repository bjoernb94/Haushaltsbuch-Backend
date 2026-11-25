package de.bochmann.haushaltsbuch.vertraege.berechnung.preis;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Die Preise eines Vertrags, sortiert nach {@code gueltigAb}. Ein Preis gilt von seinem {@code gueltigAb} bis zum
 * nächsten Eintrag; der erste gilt ab Vertragsbeginn. Vor dem ersten Eintrag gilt ebenfalls der erste, z.&nbsp;B. als
 * aktueller Preis eines Vertrags, der erst in der Zukunft beginnt.
 */
public final class Preisliste {

	/** Nach {@code gueltigAb}. */
	public static final Comparator<Preis> REIHENFOLGE = Comparator.comparing(Preis::gueltigAb);

	private final List<Preis> preise;

	/** @param preise mindestens ein Eintrag, in beliebiger Reihenfolge */
	public Preisliste(List<Preis> preise) {
		if (preise.isEmpty()) {
			throw new IllegalArgumentException("A contract needs at least one price.");
		}
		this.preise = preise.stream().sorted(REIHENFOLGE).toList();
	}

	/** Der Preis, der an {@code datum} gilt; vor dem ersten Eintrag der erste. */
	public Betrag am(LocalDate datum) {
		return eintragAm(datum).betrag();
	}

	/** Der Eintrag, der an {@code datum} gilt; vor dem ersten Eintrag der erste. */
	public Preis eintragAm(LocalDate datum) {
		return preise.get(index(datum));
	}

	/** Der letzte Eintrag mit {@code gueltigAb} bis einschließlich {@code datum}; leer, wenn alle später gelten. */
	public Optional<Preis> letzterBis(LocalDate datum) {
		Preis preis = eintragAm(datum);
		return preis.gueltigAb().isAfter(datum) ? Optional.empty() : Optional.of(preis);
	}

	/**
	 * Die nächste angekündigte Änderung nach {@code heute}: der Eintrag nach dem heute gültigen. Beginnt der Vertrag
	 * erst in der Zukunft, ist der erste Eintrag keine Änderung (er ist schon der aktuelle Betrag). Einträge nach
	 * {@code ende} zählen nicht.
	 *
	 * @param ende letzter Tag des Vertrags; leer, wenn er nicht endet
	 */
	public Optional<Preisaenderung> naechsteAenderung(LocalDate heute, Optional<LocalDate> ende) {
		int naechster = index(heute) + 1;
		if (naechster >= preise.size()) {
			return Optional.empty();
		}
		Preis preis = preise.get(naechster);
		if (ende.isPresent() && preis.gueltigAb().isAfter(ende.get())) {
			return Optional.empty();
		}
		return Optional.of(new Preisaenderung(preis.gueltigAb(), preis.betrag(), Preisaenderungsart.ANGEKUENDIGT));
	}

	/** Summe der Abbuchungen, jede mit dem an ihrem Tag gültigen Preis; ohne Abbuchungen 0 in der Währung des ersten. */
	public Betrag summe(List<LocalDate> abbuchungen) {
		return abbuchungen.stream()
				.map(this::am)
				.reduce(Betrag.nullIn(waehrung()), Betrag::plus);
	}

	/** Die Währung des Vertrags: die des ersten Preises (alle haben dieselbe; eine eigene am Vertrag kommt mit #30). */
	public Currency waehrung() {
		return preise.getFirst().betrag().waehrung();
	}

	public List<Preis> preise() {
		return preise;
	}

	/** Der Eintrag mit dem spätesten {@code gueltigAb}; bei ihm setzt die {@link Preisprognose} an. */
	public Preis letzter() {
		return preise.getLast();
	}

	/** Diese Liste ergänzt um {@code weitere} Einträge, z.&nbsp;B. die errechneten oder prognostizierten. */
	public Preisliste mit(List<Preis> weitere) {
		return weitere.isEmpty() ? this : new Preisliste(Stream.concat(preise.stream(), weitere.stream()).toList());
	}

	/** Index des an {@code datum} gültigen Eintrags; 0, wenn das Datum vor allen liegt. */
	int index(LocalDate datum) {
		int gueltig = 0;
		for (int i = 1; i < preise.size(); i++) {
			if (preise.get(i).gueltigAb().isAfter(datum)) {
				break;
			}
			gueltig = i;
		}
		return gueltig;
	}

}
