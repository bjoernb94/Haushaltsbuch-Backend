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
 * Das Laufzeitende liegt nicht vor dem aktuellen Beginn (der letzten Wiederaufnahme); der Verstoß hängt am Feld {@code laufzeitende}. In der Vorschau gegen die mitgeschickten Unterbrechungen, beim
 * Ändern prüft der Service dieselbe Regel gegen die gespeicherten (siehe {@link Unterbrechungsregeln}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LaufzeitendeNachAktuellemBeginn.Pruefung.class)
@interface LaufzeitendeNachAktuellemBeginn {

	String message() default "Laufzeitende liegt vor der letzten Wiederaufnahme";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<LaufzeitendeNachAktuellemBeginn, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			// Nicht über daten(): Dort fehlt kuendbar womöglich noch (Pflicht, eigener Verstoß).
			var unterbrechungen = anfrage.unterbrechungenFuerBerechnung();
			if (Unterbrechungsregeln.laufzeitendeNachAktuellemBeginn(anfrage.laufzeitende(), unterbrechungen)) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode("laufzeitende")
					.addConstraintViolation();
			return false;
		}

	}

}
