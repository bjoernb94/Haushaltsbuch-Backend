/**
 * Gemeinsames Modul: Security-Konfiguration, Systemstatus (Health), Fehlerbehandlung und gemeinsame Typen.
 * <p>
 * Als {@code OPEN} deklariert, damit alle Fachmodule die hier liegenden Typen
 * (inkl. Unterpakete) verwenden dürfen.
 */
@ApplicationModule(displayName = "Shared", type = ApplicationModule.Type.OPEN)
package de.bochmann.haushaltsbuch.shared;

import org.springframework.modulith.ApplicationModule;
