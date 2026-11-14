package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Die Prognose künftiger Preise aus einer {@link Preissteigerung}; nur berechnet, nie gespeichert.
 * <p>
 * Sie setzt beim letzten Eintrag der Preisliste an und zählt von {@code naechsteAm} aus im Rhythmus vorwärts.
 * Übersprungen werden Termine bis einschließlich des letzten Eintrags, bis einschließlich {@code garantieBis}, vor
 * heute und nach dem Vertragsende; übersprungene Termine erhöhen nichts. Trägt der Nutzer den echten neuen Preis ein,
 * rechnet die Prognose also von dort weiter.
 *
 * @param steigerung ohne Regel gibt es keine Prognose
 * @param garantieBis bis einschließlich dieses Tages erhöht die Prognose nicht
 */
public record Preisprognose(@Nullable Preissteigerung steigerung, @Nullable LocalDate garantieBis) {

	public static final Preisprognose KEINE = new Preisprognose(null, null);

	/**
	 * Die prognostizierten Preise in der Reihenfolge ihrer Termine.
	 *
	 * @param ende letzter Tag des Vertrags; leer, wenn er nicht endet
	 * @param bis Termine danach zählen nicht
	 * @param hoechstens so viele Preise höchstens
	 */
	List<Preis> preise(Preisliste preise, LocalDate heute, Optional<LocalDate> ende, LocalDate bis, int hoechstens) {
		if (steigerung == null) {
			return List.of();
		}
		// Der letzte Tag, an dem noch nicht erhöht wird.
		LocalDate grenze = heute.minusDays(1);
		LocalDate letzter = preise.letzter().gueltigAb();
		if (letzter != null && letzter.isAfter(grenze)) {
			grenze = letzter;
		}
		if (garantieBis != null && garantieBis.isAfter(grenze)) {
			grenze = garantieBis;
		}
		int schritt = steigerung.rhythmus().monate();
		LocalDate anker = steigerung.naechsteAm();
		// Geschätzt und dann vorgezählt, damit weit zurückliegende Anker nicht Schritt für Schritt zählen.
		long k = Math.max(0, ChronoUnit.MONTHS.between(anker, grenze) / schritt - 1);
		while (!anker.plusMonths(k * schritt).isAfter(grenze)) {
			k++;
		}
		List<Preis> prognose = new ArrayList<>();
		var preis = preise.letzter().betrag();
		for (LocalDate termin = anker.plusMonths(k * schritt);
				prognose.size() < hoechstens && !termin.isAfter(bis) && (ende.isEmpty() || !termin.isAfter(ende.get()));
				termin = anker.plusMonths(++k * schritt)) {
			preis = steigerung.erhoeht(preis);
			prognose.add(new Preis(termin, preis));
		}
		return prognose;
	}

}
