/**
 * Modul Haushalt: Besitzer der Fachdaten, seine Mitglieder und die gebuchten Komponenten.
 * <p>
 * Öffentliche API: {@link de.bochmann.haushaltsbuch.haushalt.HaushaltService} zum Anlegen bei der
 * Registrierung und {@link de.bochmann.haushaltsbuch.haushalt.AktuellerHaushalt}, über den die
 * Fachmodule den Haushalt des angemeldeten Nutzers ermitteln.
 */
@ApplicationModule(displayName = "Haushalt")
package de.bochmann.haushaltsbuch.haushalt;

import org.springframework.modulith.ApplicationModule;
