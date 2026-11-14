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
 * Kein Preis gilt ab einem Tag vor dem Vertragsbeginn, auch nicht, wenn der Beginn später nach hinten verschoben wird
 * ({@code PUT} prüft die ganze Liste); der Verstoß hängt an {@code preise[i].gueltigAb}. Beim Speichern wie in der
 * Vorschau; unvollständige Zeilen zählen nicht.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PreisNichtVorBeginn.Pruefung.class)
@interface PreisNichtVorBeginn {

	String message() default "Preis gilt vor dem Vertragsbeginn";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<PreisNichtVorBeginn, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			if (anfrage.beginn() == null || anfrage.preise() == null) {
				return true;
			}
			boolean gueltig = true;
			for (int i = 0; i < anfrage.preise().size(); i++) {
				var preis = anfrage.preise().get(i);
				if (preis != null && preis.vollstaendig() && preis.gueltigAb() != null
						&& preis.gueltigAb().isBefore(anfrage.beginn())) {
					context.disableDefaultConstraintViolation();
					context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
							.addPropertyNode("preise")
							.addPropertyNode("gueltigAb").inIterable().atIndex(i)
							.addConstraintViolation();
					gueltig = false;
				}
			}
			return gueltig;
		}

	}

}
