package de.bochmann.haushaltsbuch.vertraege.vertrag;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Der früheste Preis gilt ab dem Vertragsbeginn, nicht erst später; der Verstoß hängt an {@code preise[i].gueltigAb}
 * dieses Eintrags (bei gleichem Datum am ersten). Liegt er davor, meldet das {@link PreisNichtVorBeginn}. Beim
 * Speichern wie in der Vorschau; unvollständige Zeilen zählen nicht, ohne {@code beginn} oder vollständige Zeile prüft
 * die Regel nichts.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ErsterPreisAbBeginn.Pruefung.class)
@interface ErsterPreisAbBeginn {

	String message() default "Der früheste Preis gilt nicht ab dem Vertragsbeginn";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<ErsterPreisAbBeginn, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			if (anfrage.beginn() == null || anfrage.preise() == null) {
				return true;
			}
			int frueheste = -1;
			for (int i = 0; i < anfrage.preise().size(); i++) {
				var preis = anfrage.preise().get(i);
				if (preis != null && preis.vollstaendig() && (frueheste < 0
						|| preis.gueltigAb().isBefore(anfrage.preise().get(frueheste).gueltigAb()))) {
					frueheste = i;
				}
			}
			if (frueheste < 0 || !anfrage.preise().get(frueheste).gueltigAb().isAfter(anfrage.beginn())) {
				return true;
			}
			return Preisregeln.verstossInPreisen(context, frueheste, "gueltigAb");
		}

	}

}
