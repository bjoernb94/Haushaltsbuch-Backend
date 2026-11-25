package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Das Laufzeitende liegt nicht vor dem Beginn; der Verstoß hängt am Feld {@code laufzeitende}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LaufzeitendeNachBeginn.Pruefung.class)
public @interface LaufzeitendeNachBeginn {

	String message() default "Laufzeitende liegt vor dem Beginn";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<LaufzeitendeNachBeginn, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			if (anfrage.beginn() == null || anfrage.laufzeitende() == null
					|| !anfrage.laufzeitende().isBefore(anfrage.beginn())) {
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
