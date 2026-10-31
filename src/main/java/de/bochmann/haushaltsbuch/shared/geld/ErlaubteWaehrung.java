package de.bochmann.haushaltsbuch.shared.geld;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Currency;
import java.util.Set;

/**
 * Nur Währungen, die die Anwendung schon unterstützt; vorerst nur EUR (weitere mit Issue #30).
 * In Fehlerantworten erscheint der Name als {@code code} in {@code felder}.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ErlaubteWaehrung.Pruefung.class)
@interface ErlaubteWaehrung {

	String message() default "Währung wird nicht unterstützt";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<ErlaubteWaehrung, Currency> {

		static final Set<Currency> ERLAUBT = Set.of(Betrag.EUR);

		@Override
		public boolean isValid(Currency waehrung, ConstraintValidatorContext context) {
			return waehrung == null || ERLAUBT.contains(waehrung);
		}

	}

}
