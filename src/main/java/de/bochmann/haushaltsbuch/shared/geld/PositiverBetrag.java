package de.bochmann.haushaltsbuch.shared.geld;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Betrag größer als 0, z.&nbsp;B. für Vertragspreise ({@link Betrag} selbst erlaubt auch negative Werte).
 * Leere Beträge prüft {@code @NotNull}. In Fehlerantworten erscheint der Name als {@code code} in
 * {@code felder}, am Feld des Betrags (z.&nbsp;B. {@code betrag}).
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiverBetrag.Pruefung.class)
public @interface PositiverBetrag {

	String message() default "Betrag muss größer als 0 sein";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<PositiverBetrag, Betrag> {

		@Override
		public boolean isValid(Betrag betrag, ConstraintValidatorContext context) {
			return betrag == null || betrag.wert().signum() > 0;
		}

	}

}
