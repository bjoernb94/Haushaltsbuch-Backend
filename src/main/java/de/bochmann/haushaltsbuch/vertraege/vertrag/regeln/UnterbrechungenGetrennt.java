package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Die Unterbrechungen der Vorschau überschneiden sich nicht, {@code bis} liegt nicht vor {@code von}, und zwischen
 * zweien läuft der Vertrag mindestens einen Tag; der Verstoß hängt an {@code unterbrechungen[i].von}. Unvollständige
 * Zeilen zählen nicht. Gespeicherte Unterbrechungen sind immer getrennt, weil sie nur die Wiederaufnahme anlegt.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UnterbrechungenGetrennt.Pruefung.class)
public @interface UnterbrechungenGetrennt {

	String message() default "Unterbrechungen überschneiden sich";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<UnterbrechungenGetrennt, List<? extends Unterbrechungsangabe>> {

		@Override
		public boolean isValid(List<? extends Unterbrechungsangabe> unterbrechungen, ConstraintValidatorContext context) {
			if (unterbrechungen == null) {
				return true;
			}
			var vollstaendig = unterbrechungen.stream().filter(Objects::nonNull)
					.filter(Unterbrechungsangabe::vollstaendig)
					.sorted(Comparator.comparing(Unterbrechungsangabe::von))
					.toList();
			int index = Unterbrechungsregeln.ersteUeberschneidung(vollstaendig.stream()
					.map(Unterbrechungsangabe::unterbrechung).toList());
			if (index < 0) {
				return true;
			}
			// Der Index in der Anfrage, nicht in der sortierten Liste, damit die SPA die Zeile findet.
			return Preisregeln.verstoss(context, unterbrechungen.indexOf(vollstaendig.get(index)), "von");
		}

	}

}
