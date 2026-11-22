package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preis;
import de.bochmann.haushaltsbuch.vertraege.berechnung.Preisherkunft;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Eine Zeile der Preisliste, beim Speichern ({@link VertragPreisAnfrage}) wie in der Vorschau
 * ({@link VertragVorschauPreis}); die Regeln über die Liste ({@link GueltigAbEindeutig} …) prüfen beide gleich.
 */
interface Preisangabe {

	/**
	 * So viele Preise darf ein Vertrag höchstens haben. Reicht auch für automatisch angelegte Stufen über die ganze
	 * erlaubte Zeit ({@code PlausiblesDatum}: 1900–2199, vierteljährlich knapp 1200 Stufen), damit ein gespeicherter
	 * Vertrag beim Zurückschicken nie an {@code Size} scheitert (#159).
	 */
	int HOECHSTENS = 1200;

	LocalDate gueltigAb();

	Betrag betrag();

	/**
	 * Wie in der Antwort: eine automatisch angelegte Stufe der Preissteigerung. Ob das stimmt, prüft das Backend
	 * beim Speichern (siehe {@link VertragPreisAnfrage#vertragPreis}); die Vorschau übernimmt es ungeprüft.
	 */
	@Nullable Boolean automatisch();

	/** Ohne Datum oder Betrag ist die Zeile unvollständig: Die Vorschau ignoriert sie, beim Speichern ist sie ungültig. */
	default boolean vollstaendig() {
		return gueltigAb() != null && betrag() != null;
	}

	default Preis preis() {
		return new Preis(gueltigAb(), betrag(),
				Boolean.TRUE.equals(automatisch()) ? Preisherkunft.AUTOMATISCH : Preisherkunft.EINGETRAGEN);
	}

}
