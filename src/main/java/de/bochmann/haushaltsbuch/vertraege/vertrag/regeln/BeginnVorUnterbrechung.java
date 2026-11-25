package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Der Beginn liegt vor der ersten Unterbrechung; der Verstoß hängt am Feld {@code beginn}. In der Vorschau gegen die mitgeschickten Unterbrechungen, beim
 * Ändern prüft der Service dieselbe Regel gegen die gespeicherten (siehe {@link Unterbrechungsregeln}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BeginnVorUnterbrechung.Pruefung.class)
public @interface BeginnVorUnterbrechung {

	String message() default "Beginn liegt nicht vor der ersten Unterbrechung";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<BeginnVorUnterbrechung, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			// Nicht über daten(): Dort fehlt kuendbar womöglich noch (Pflicht, eigener Verstoß).
			var unterbrechungen = anfrage.unterbrechungenFuerBerechnung();
			if (Unterbrechungsregeln.beginnVorUnterbrechung(anfrage.beginn(), unterbrechungen)) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode("beginn")
					.addConstraintViolation();
			return false;
		}

	}

}
