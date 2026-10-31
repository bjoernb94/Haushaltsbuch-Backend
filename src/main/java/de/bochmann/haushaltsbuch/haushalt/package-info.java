/**
 * Modul Haushalt: Besitzer der Fachdaten, seine Mitglieder und die gebuchten Komponenten.
 * <p>
 * Öffentliche API: {@link de.bochmann.haushaltsbuch.haushalt.HaushaltService} zum Anlegen bei der
 * Registrierung. Den Haushalt des angemeldeten Nutzers liefert das Modul über
 * {@code shared.fachdaten.HaushaltAbfrage} an den {@code HaushaltKontext}; Fachmodule hängen daher
 * nicht von diesem Modul ab.
 */
@ApplicationModule(displayName = "Haushalt")
package de.bochmann.haushaltsbuch.haushalt;

import org.springframework.modulith.ApplicationModule;
