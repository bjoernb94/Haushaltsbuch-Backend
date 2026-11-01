package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Kuendigung;

/**
 * Ergebnis der Vorschau; ein Objekt statt der nackten Kündigung, damit später weitere Werte dazukommen können.
 *
 * @param kuendigung wie {@code berechnet.kuendigung} eines gespeicherten Vertrags
 */
record VertragVorschauAntwort(Kuendigung kuendigung) {
}
