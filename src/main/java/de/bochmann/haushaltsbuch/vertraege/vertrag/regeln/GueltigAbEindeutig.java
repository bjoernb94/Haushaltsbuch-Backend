package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kein {@code gueltigAb} doppelt; jede Wiederholung ist ein Verstoß an {@code preise[i].gueltigAb}. Unvollständige
 * Zeilen zählen nicht.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GueltigAbEindeutig.Pruefung.class)
public @interface GueltigAbEindeutig {

	String message() default "Für diesen Tag gibt es schon einen Preis";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<GueltigAbEindeutig, List<? extends Preisangabe>> {

		@Override
		public boolean isValid(List<? extends Preisangabe> preise, ConstraintValidatorContext context) {
			if (preise == null) {
				return true;
			}
			boolean gueltig = true;
			Set<LocalDate> gesehen = new HashSet<>();
			for (int i = 0; i < preise.size(); i++) {
				if (preise.get(i) != null && preise.get(i).vollstaendig() && !gesehen.add(preise.get(i).gueltigAb())) {
					gueltig = Preisregeln.verstoss(context, i, "gueltigAb");
				}
			}
			return gueltig;
		}

	}

}
