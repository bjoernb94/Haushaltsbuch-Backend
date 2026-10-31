package de.bochmann.haushaltsbuch.nutzer;

class EmailBereitsVergebenException extends RuntimeException {

	EmailBereitsVergebenException(String email) {
		super("An account already exists for email address %s.".formatted(email));
	}

}
