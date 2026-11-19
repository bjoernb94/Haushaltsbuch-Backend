package de.bochmann.haushaltsbuch.nutzer;

import de.bochmann.haushaltsbuch.shared.Komponente;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Neue Auswahl der Einstellungsseite „Komponenten“, mindestens eine wie bei der Registrierung, und
 * die {@code version} aus {@link KomponentenAntwort}.
 */
record KomponentenAenderung(@NotEmpty Set<Komponente> komponenten, @NotNull Long version) {
}
