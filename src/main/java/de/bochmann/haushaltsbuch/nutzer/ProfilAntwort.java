package de.bochmann.haushaltsbuch.nutzer;

/** Profil des angemeldeten Nutzers, wie es im Token der Anmeldung steht. */
record ProfilAntwort(String vorname, String name, String email) {
}
