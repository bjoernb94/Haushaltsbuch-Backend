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
 * Gekündigt frühestens zum Tag der letzten Wiederaufnahme; der Verstoß hängt am Feld {@code gekuendigtZum}. In der Vorschau gegen die mitgeschickten Unterbrechungen, beim
 * Ändern prüft der Service dieselbe Regel gegen die gespeicherten (siehe {@link Unterbrechungsregeln}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KuendigungNachUnterbrechung.Pruefung.class)
@interface KuendigungNachUnterbrechung {

	String message() default "Kündigung liegt vor der letzten Wiederaufnahme";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<KuendigungNachUnterbrechung, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			// Nicht über daten(): Dort fehlt kuendbar womöglich noch (Pflicht, eigener Verstoß).
			var unterbrechungen = anfrage.unterbrechungenFuerBerechnung();
			if (Unterbrechungsregeln.kuendigungNachUnterbrechung(anfrage.gekuendigtZum(), unterbrechungen)) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode("gekuendigtZum")
					.addConstraintViolation();
			return false;
		}

	}

}
