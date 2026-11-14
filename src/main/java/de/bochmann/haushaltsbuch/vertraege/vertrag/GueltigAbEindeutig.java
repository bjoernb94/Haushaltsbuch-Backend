package de.bochmann.haushaltsbuch.vertraege.vertrag;

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

/** Kein {@code gueltigAb} doppelt; jede Wiederholung ist ein Verstoß an {@code preise[i].gueltigAb}. */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GueltigAbEindeutig.Pruefung.class)
@interface GueltigAbEindeutig {

	String message() default "Für diesen Tag gibt es schon einen Preis";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<GueltigAbEindeutig, List<VertragPreisAnfrage>> {

		@Override
		public boolean isValid(List<VertragPreisAnfrage> preise, ConstraintValidatorContext context) {
			if (preise == null) {
				return true;
			}
			boolean gueltig = true;
			Set<LocalDate> gesehen = new HashSet<>();
			for (int i = 0; i < preise.size(); i++) {
				LocalDate gueltigAb = preise.get(i) == null ? null : preise.get(i).gueltigAb();
				// Mehrere ohne Datum meldet GueltigAbPflicht.
				if (gueltigAb != null && !gesehen.add(gueltigAb)) {
					gueltig = Preisregeln.verstoss(context, i, "gueltigAb");
				}
			}
			return gueltig;
		}

	}

}
