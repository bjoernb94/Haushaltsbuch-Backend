package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Alles, woraus {@link Vertragsberechnung#werte} rechnet: beim gespeicherten Vertrag vollständig, in der Vorschau
 * eines halb ausgefüllten Formulars mit Lücken.
 *
 * @param preise leer, wenn noch kein Preis vollständig ist
 * @param einmalkosten nur vollständige Einträge
 */
public record Berechnungsgrundlage(
		Vertragsdaten daten,
		@Nullable Preisliste preise,
		Preisprognose prognose,
		Anteil anteil,
		List<Einmalkosten> einmalkosten) {
}
