package de.bochmann.haushaltsbuch.shared.zeit;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * Fachliches Datum zwischen {@link #VON} und {@link #BIS}, z.&nbsp;B. Vertragsbeginn oder Buchungsdatum. Ohne die
 * Grenze ergäben Werte wie {@code +999999999-12-31} beim Weiterzählen einen Überlauf oder ließen sich in Postgres
 * nicht speichern (500 statt 400). Leere Werte prüft {@code @NotNull}. In Fehlerantworten erscheint der Name als
 * {@code code} in {@code felder}.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlausiblesDatum.Pruefung.class)
public @interface PlausiblesDatum {

	LocalDate VON = LocalDate.of(1900, 1, 1);

	LocalDate BIS = LocalDate.of(2199, 12, 31);

	String message() default "Datum muss zwischen 1900-01-01 und 2199-12-31 liegen";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<PlausiblesDatum, LocalDate> {

		@Override
		public boolean isValid(LocalDate datum, ConstraintValidatorContext context) {
			return datum == null || (!datum.isBefore(VON) && !datum.isAfter(BIS));
		}

	}

}
