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
 * Alle Beträge des Vertrags in derselben Währung wie der erste Preis; eine Währung am Vertrag selbst kommt mit #30.
 * <ul>
 *   <li>An {@code preise}: jeder abweichende Preis ist ein Verstoß an {@code preise[i].betrag.waehrung}.</li>
 *   <li>Am Vertrag: jeder abweichende Eintrag der Einmalkosten ist ein Verstoß an
 *   {@code einmalkosten[i].betrag.waehrung}, ein abweichender Betrag der Preissteigerung an
 *   {@code preissteigerung.betrag.waehrung}. Ohne gültigen ersten Preis prüft das Feld {@code preise}.</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {GleicheWaehrung.Pruefung.class, GleicheWaehrung.VertragPruefung.class})
@interface GleicheWaehrung {

	String message() default "Alle Beträge brauchen dieselbe Währung";

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

	class VertragPruefung implements ConstraintValidator<GleicheWaehrung, Vertragsangaben> {

		@Override
		public boolean isValid(Vertragsangaben anfrage, ConstraintValidatorContext context) {
			if (anfrage.preise() == null || anfrage.preise().isEmpty() || anfrage.preise().getFirst() == null
					|| anfrage.preise().getFirst().betrag() == null) {
				return true;
			}
			Currency waehrung = anfrage.preise().getFirst().betrag().waehrung();
			boolean gueltig = true;
			var steigerung = anfrage.preissteigerung();
			if (steigerung != null && steigerung.betrag() != null && steigerung.betrag().waehrung() != null
					&& !steigerung.betrag().waehrung().equals(waehrung)) {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
						.addPropertyNode("preissteigerung")
						.addPropertyNode("betrag")
						.addPropertyNode("waehrung")
						.addConstraintViolation();
				gueltig = false;
			}
			for (int i = 0; anfrage.einmalkosten() != null && i < anfrage.einmalkosten().size(); i++) {
				var eintrag = anfrage.einmalkosten().get(i);
				if (eintrag != null && eintrag.betrag() != null && eintrag.betrag().waehrung() != null
						&& !eintrag.betrag().waehrung().equals(waehrung)) {
					context.disableDefaultConstraintViolation();
					context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
							.addPropertyNode("einmalkosten")
							.addPropertyNode("betrag").inIterable().atIndex(i)
							.addPropertyNode("waehrung")
							.addConstraintViolation();
					gueltig = false;
				}
			}
			return gueltig;
		}

	}

}
