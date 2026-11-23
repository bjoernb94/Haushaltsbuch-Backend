package de.bochmann.haushaltsbuch.vertraege.berechnung;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Was eine {@link Preissteigerung} aus der Preisliste macht: die {@link #faellige fälligen Stufen} bis zu einem Tag
 * und die {@link #preise Prognose} danach. Gespeichert wird nur die Regel und, beim Speichern des Vertrags, die
 * fälligen Stufen als automatische Preise (ADR 0022).
 * <p>
 * Die Termine zählen von {@code naechsteAm} aus im Rhythmus; liegt {@code naechsteAm} auf einem Monatsletzten,
 * bleiben auch die weiteren am Monatsletzten (30.04. → 31.07.). Bis einschließlich {@code garantieBis} erhöht kein
 * Termin; übersprungene Termine erhöhen nichts. Jeder Schritt wird gerundet, der nächste rechnet mit dem gerundeten
 * weiter (ADR 0011).
 *
 * @param steigerung ohne Regel gibt es weder Stufen noch Prognose
 * @param garantieBis bis einschließlich dieses Tages erhöht kein Termin
 * @param betrachtetBis bis einschließlich dieses Tages hat das Speichern die Termine schon betrachtet und fällige
 *        Stufen angelegt; ein Termin bis dahin wird nie wieder fällig, auch wenn der Nutzer seinen Preis gelöscht hat
 *        (#162). Leer, wenn noch nie mit Regel gespeichert
 */
public record Preisprognose(@Nullable Preissteigerung steigerung, @Nullable LocalDate garantieBis,
		@Nullable LocalDate betrachtetBis) {

	public static final Preisprognose KEINE = new Preisprognose(null, null, null);

	/** Noch nie gespeichert. */
	public Preisprognose(@Nullable Preissteigerung steigerung, @Nullable LocalDate garantieBis) {
		this(steigerung, garantieBis, null);
	}

	/** Diese Prognose, nachdem das Speichern die Termine bis einschließlich {@code tag} betrachtet hat. */
	public Preisprognose betrachtetBis(LocalDate tag) {
		return new Preisprognose(steigerung, garantieBis, spaeter(betrachtetBis, tag));
	}

	/**
	 * Die fälligen Stufen bis einschließlich {@code bis}: Sie setzen beim letzten Eintrag bis zu diesem Tag an (dem
	 * Ausgangseintrag), egal ob eingetragen oder automatisch; spätere, angekündigte Einträge zählen dafür nicht.
	 * Termine bis einschließlich {@code betrachtetBis} sind nie fällig: Eine gelöschte Stufe bleibt gelöscht, auch die
	 * letzte. Zwischen früheren Einträgen rechnet die Regel nicht, eine Lücke dort ist gewollt. Leer ohne Regel oder
	 * wenn alle Einträge erst nach {@code bis} gelten.
	 *
	 * @param bis heute bzw. der letzte Tag eines beendeten Vertrags
	 * @param herkunft {@link Preisherkunft#AUTOMATISCH} beim Speichern, {@link Preisherkunft#ERRECHNET} beim Lesen
	 */
	public List<Preis> faellige(Preisliste preise, LocalDate bis, Preisherkunft herkunft) {
		if (steigerung == null) {
			return List.of();
		}
		return preise.letzterBis(bis)
				.map(ausgang -> stufen(ausgang.betrag(), ohneGarantie(spaeter(betrachtetBis, ausgang.gueltigAb())), bis,
						Integer.MAX_VALUE, herkunft))
				.orElse(List.of());
	}

	/**
	 * Die prognostizierten Preise nach heute in der Reihenfolge ihrer Termine. Sie setzen beim letzten Eintrag der
	 * Preisliste an, die also schon um die {@link #faellige fälligen Stufen} bis heute ergänzt sein muss; Termine bis
	 * einschließlich dieses Eintrags zählen nicht.
	 *
	 * @param ende letzter Tag des Vertrags; leer, wenn er nicht endet
	 * @param bis Termine danach zählen nicht
	 * @param hoechstens so viele Preise höchstens
	 */
	List<Preis> preise(Preisliste preise, LocalDate heute, Optional<LocalDate> ende, LocalDate bis, int hoechstens) {
		if (steigerung == null) {
			return List.of();
		}
		Preis letzter = preise.letzter();
		LocalDate grenze = letzter.gueltigAb().isAfter(heute) ? letzter.gueltigAb() : heute;
		LocalDate letzterTermin = ende.isPresent() && ende.get().isBefore(bis) ? ende.get() : bis;
		return stufen(letzter.betrag(), ohneGarantie(grenze), letzterTermin, hoechstens, Preisherkunft.PROGNOSE);
	}

	/** {@code grenze}, mindestens aber {@code garantieBis}: der letzte Tag, an dem noch nicht erhöht wird. */
	private LocalDate ohneGarantie(LocalDate grenze) {
		return spaeter(garantieBis, grenze);
	}

	/** Der spätere der beiden Tage; {@code tag}, wenn {@code vielleicht} leer ist. */
	private static LocalDate spaeter(@Nullable LocalDate vielleicht, LocalDate tag) {
		return vielleicht != null && vielleicht.isAfter(tag) ? vielleicht : tag;
	}

	/** Die Stufen an den Terminen nach {@code grenze} bis einschließlich {@code bis}, ausgehend von {@code preis}. */
	private List<Preis> stufen(Betrag preis, LocalDate grenze, LocalDate bis, int hoechstens, Preisherkunft herkunft) {
		long k = ersterTerminNach(grenze);
		List<Preis> stufen = new ArrayList<>();
		for (LocalDate termin = termin(k); stufen.size() < hoechstens && !termin.isAfter(bis); termin = termin(++k)) {
			preis = steigerung.erhoeht(preis);
			stufen.add(new Preis(termin, preis, herkunft));
		}
		return stufen;
	}

	/** Der Index des ersten Termins nach {@code grenze}; geschätzt und dann vorgezählt, nicht Schritt für Schritt. */
	private long ersterTerminNach(LocalDate grenze) {
		long k = Math.max(0, ChronoUnit.MONTHS.between(steigerung.naechsteAm(), grenze) / schritt() - 1);
		while (!termin(k).isAfter(grenze)) {
			k++;
		}
		return k;
	}

	/** Der {@code k}-te Termin ab {@code naechsteAm}, immer von dort gezählt, damit Monatsenden nicht abrutschen. */
	private LocalDate termin(long k) {
		return Vertragsberechnung.plusMonate(steigerung.naechsteAm(), k * schritt());
	}

	private int schritt() {
		return steigerung.rhythmus().monate();
	}

}
