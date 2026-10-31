package de.bochmann.haushaltsbuch.shared.fehler;

import java.util.Map;

/**
 * Fachlich ungültige Angabe, die Bean Validation nicht prüfen kann, z.&nbsp;B. eine unbekannte oder
 * fremde Kategorie: 400 mit Code {@value Probleme#VALIDIERUNG} und dem Feld in {@code felder}, genau
 * wie bei {@code @Valid}. Die SPA behandelt beide Fälle gleich.
 * <p>
 * {@code code} ist wie bei Constraints ein Name in PascalCase (z.&nbsp;B. {@code Unbekannt}), damit
 * er sich in {@code felder} nicht von Annotationsnamen wie {@code NotBlank} unterscheidet.
 */
public class UngueltigeEingabeException extends RuntimeException {

	private final String feld;

	private final String code;

	private final Map<String, Object> parameter;

	/**
	 * @param feld Pfad des Feldes im Request-Body wie bei {@code @Valid}, z.&nbsp;B. {@code kategorieId}
	 * @param code Name des Verstoßes in PascalCase, z.&nbsp;B. {@code Unbekannt}
	 * @param detail englische Beschreibung für Entwickler, z.&nbsp;B. {@code "Category 42 not found."}
	 */
	public UngueltigeEingabeException(String feld, String code, String detail) {
		this(feld, code, Map.of(), detail);
	}

	/** Wie {@link #UngueltigeEingabeException(String, String, String)}, mit Parametern für den Text der SPA. */
	public UngueltigeEingabeException(String feld, String code, Map<String, Object> parameter, String detail) {
		super(detail);
		this.feld = feld;
		this.code = code;
		this.parameter = Map.copyOf(parameter);
	}

	public String getFeld() {
		return feld;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getParameter() {
		return parameter;
	}

}
