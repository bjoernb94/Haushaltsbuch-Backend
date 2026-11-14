package de.bochmann.haushaltsbuch.vertraege.vertrag;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Currency;
import java.util.List;

/**
 * Alle Preise in derselben Währung wie der erste; jeder abweichende ist ein Verstoß an
 * {@code preise[i].betrag.waehrung}. Eine Währung am Vertrag selbst kommt mit #30.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GleicheWaehrung.Pruefung.class)
@interface GleicheWaehrung {

	String message() default "Alle Preise brauchen dieselbe Währung";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<GleicheWaehrung, List<VertragPreisAnfrage>> {

		@Override
		public boolean isValid(List<VertragPreisAnfrage> preise, ConstraintValidatorContext context) {
			if (preise == null) {
				return true;
			}
			boolean gueltig = true;
			Currency erste = null;
			for (int i = 0; i < preise.size(); i++) {
				Currency waehrung = preise.get(i) == null || preise.get(i).betrag() == null ? null
						: preise.get(i).betrag().waehrung();
				if (waehrung == null) {
					continue;
				}
				if (erste == null) {
					erste = waehrung;
				}
				else if (!erste.equals(waehrung)) {
					gueltig = Preisregeln.verstoss(context, i, "betrag", "waehrung");
				}
			}
			return gueltig;
		}

	}

}
