package de.bochmann.haushaltsbuch.nutzer;

class EmailBereitsVergebenException extends RuntimeException {

	EmailBereitsVergebenException(String email) {
		super("Für die E-Mail-Adresse %s existiert bereits ein Konto.".formatted(email));
	}

}
