package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.vertraege.berechnung.Steigerungsart;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Zur {@code art} der Preissteigerung passt genau ein Wert: bei {@code PROZENT} {@code prozent}, bei {@code BETRAG}
 * {@code betrag}. Ein fehlender oder unpassender Wert ist ein Verstoß an diesem Feld, z.&nbsp;B.
 * {@code preissteigerung.prozent}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PassenderWert.Pruefung.class)
@interface PassenderWert {

	String message() default "Der Wert passt nicht zur Art der Preissteigerung";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<PassenderWert, VertragPreissteigerungAnfrage> {

		@Override
		public boolean isValid(VertragPreissteigerungAnfrage steigerung, ConstraintValidatorContext context) {
			if (steigerung.art() == null) {
				return true;
			}
			boolean prozent = steigerung.art() == Steigerungsart.PROZENT;
			boolean gueltig = true;
			if (prozent != (steigerung.prozent() != null)) {
				gueltig = verstoss(context, "prozent");
			}
			if (prozent == (steigerung.betrag() != null)) {
				gueltig = verstoss(context, "betrag");
			}
			return gueltig;
		}

		private static boolean verstoss(ConstraintValidatorContext context, String feld) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
					.addPropertyNode(feld)
					.addConstraintViolation();
			return false;
		}

	}

}
