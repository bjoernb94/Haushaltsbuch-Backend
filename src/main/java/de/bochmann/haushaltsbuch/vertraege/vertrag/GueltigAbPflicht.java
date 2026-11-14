package de.bochmann.haushaltsbuch.vertraege.vertrag;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Nur ein Preis darf ohne {@code gueltigAb} sein (er gilt ab Vertragsbeginn); jeder weitere ohne Datum ist ein
 * Verstoß an {@code preise[i].gueltigAb}. Unvollständige Zeilen (ohne Betrag) zählen nicht.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GueltigAbPflicht.Pruefung.class)
@interface GueltigAbPflicht {

	String message() default "Nur der erste Preis darf ohne Gültigkeitsdatum sein";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<GueltigAbPflicht, List<? extends Preisangabe>> {

		@Override
		public boolean isValid(List<? extends Preisangabe> preise, ConstraintValidatorContext context) {
			if (preise == null) {
				return true;
			}
			boolean gueltig = true;
			boolean ohneDatum = false;
			for (int i = 0; i < preise.size(); i++) {
				if (preise.get(i) == null || !preise.get(i).vollstaendig() || preise.get(i).gueltigAb() != null) {
					continue;
				}
				if (ohneDatum) {
					gueltig = Preisregeln.verstoss(context, i, "gueltigAb");
				}
				ohneDatum = true;
			}
			return gueltig;
		}

	}

}
