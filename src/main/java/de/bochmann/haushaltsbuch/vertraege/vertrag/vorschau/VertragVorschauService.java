package de.bochmann.haushaltsbuch.vertraege.vertrag.vorschau;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Vertragsberechnung;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Die Vorschau des Vertragsformulars: rechnet, ohne zu speichern, und liest keine gespeicherten Verträge. Was die
 * SPA zum gespeicherten Vertrag braucht (z.&nbsp;B. die verbleibenden Unterbrechungen), schickt sie mit.
 */
@Service
public class VertragVorschauService {

	private final Clock clock;

	VertragVorschauService(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Die berechneten Werte zu den Formularwerten; dieselbe Berechnung wie für gespeicherte Verträge, mit den Preisen,
	 * wie sie nach dem Speichern wären (samt den Stufen, die das Speichern anlegt).
	 */
	public VertragVorschauAntwort vorschau(VertragVorschauAnfrage anfrage) {
		var berechnung = new Vertragsberechnung(LocalDate.now(clock));
		var grundlage = berechnung.wieGespeichert(anfrage.grundlage());
		return VertragVorschauAntwort.aus(berechnung.werte(grundlage), grundlage, anfrage);
	}

}
