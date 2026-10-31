package de.bochmann.haushaltsbuch.shared.geld;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Geldbetrag mit Währung, z.&nbsp;B. für Buchungen und Vertragspreise.
 * <ul>
 *   <li><b>Datenbank:</b> als {@code @Embedded} zwei Spalten {@code <feld>_wert NUMERIC(19, 2)} und
 *   {@code <feld>_waehrung VARCHAR(3)} (Namensschema siehe {@code application.yml}).</li>
 *   <li><b>JSON:</b> {@code {"wert": 12.34, "waehrung": "EUR"}}; der Wert ist eine Zahl. Summen und
 *   andere Berechnungen macht das Backend, die SPA formatiert nur.</li>
 *   <li><b>Validierung:</b> Request-DTOs markieren das Feld mit {@code @NotNull @Valid}; dann prüft
 *   die Validierung Nachkommastellen ({@code Digits}) und Währung ({@code ErlaubteWaehrung}).</li>
 * </ul>
 * Vorerst ist nur {@link #EUR} erlaubt; der Typ ist aber für weitere Währungen ausgelegt (Issue #30).
 * Rechnen geht deshalb nur mit Beträgen derselben Währung, Summen gruppieren nach Währung. Beträge
 * dürfen negativ sein.
 */
@Embeddable
public record Betrag(
		@Digits(integer = 17, fraction = NACHKOMMASTELLEN) @Column(precision = 19, scale = NACHKOMMASTELLEN)
		BigDecimal wert,
		@ErlaubteWaehrung @Column(length = 3)
		Currency waehrung) {

	public static final Currency EUR = Currency.getInstance("EUR");

	static final int NACHKOMMASTELLEN = 2;

	/** Kaufmännisch: 2,345 → 2,35. */
	public static final RoundingMode RUNDUNG = RoundingMode.HALF_UP;

	/**
	 * Ergänzt fehlende Nachkommastellen (12.3 → 12.30), damit gleiche Beträge gleich sind. Mehr als
	 * zwei Nachkommastellen werden nicht gerundet, sondern von der Validierung abgelehnt; Berechnungen
	 * runden selbst (siehe {@link #mal}).
	 */
	public Betrag {
		Objects.requireNonNull(wert, "wert");
		Objects.requireNonNull(waehrung, "waehrung");
		if (wert.scale() < NACHKOMMASTELLEN) {
			wert = wert.setScale(NACHKOMMASTELLEN);
		}
	}

	public static Betrag euro(String wert) {
		return euro(new BigDecimal(wert));
	}

	public static Betrag euro(BigDecimal wert) {
		return new Betrag(wert, EUR);
	}

	public static Betrag nullIn(Currency waehrung) {
		return new Betrag(BigDecimal.ZERO, waehrung);
	}

	public Betrag plus(Betrag anderer) {
		return new Betrag(wert.add(gleicheWaehrung(anderer).wert), waehrung);
	}

	public Betrag minus(Betrag anderer) {
		return new Betrag(wert.subtract(gleicheWaehrung(anderer).wert), waehrung);
	}

	public Betrag negiert() {
		return new Betrag(wert.negate(), waehrung);
	}

	/** Z.&nbsp;B. für Anteile oder Zinsen; das Ergebnis wird kaufmännisch gerundet. */
	public Betrag mal(BigDecimal faktor) {
		return new Betrag(wert.multiply(faktor).setScale(NACHKOMMASTELLEN, RUNDUNG), waehrung);
	}

	public boolean kleinerNull() {
		return wert.signum() < 0;
	}

	private Betrag gleicheWaehrung(Betrag anderer) {
		if (!waehrung.equals(anderer.waehrung)) {
			throw new IllegalArgumentException("Beträge in %s und %s lassen sich nicht verrechnen."
					.formatted(waehrung, anderer.waehrung));
		}
		return anderer;
	}

}
