package de.bochmann.haushaltsbuch.vertraege.vertrag;

import de.bochmann.haushaltsbuch.shared.geld.Betrag;
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
 *   {@code preissteigerung.betrag.waehrung}. Ohne Preis (nur in der Vorschau möglich) zählt die Währung der ersten
 *   Einmalkosten.</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {GleicheWaehrung.Pruefung.class, GleicheWaehrung.VertragPruefung.class})
@interface GleicheWaehrung {

	String message() default "Alle Beträge brauchen dieselbe Währung";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Pruefung implements ConstraintValidator<GleicheWaehrung, List<? extends Preisangabe>> {

		@Override
		public boolean isValid(List<? extends Preisangabe> preise, ConstraintValidatorContext context) {
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

	class VertragPruefung implements ConstraintValidator<GleicheWaehrung, Berechnungsangaben> {

		@Override
		public boolean isValid(Berechnungsangaben anfrage, ConstraintValidatorContext context) {
			// Die Währung des ersten Preises; ohne Preis (nur in der Vorschau) die der ersten Einmalkosten.
			Currency waehrung = erste(anfrage.preise() == null ? null
					: anfrage.preise().stream().map(preis -> preis == null ? null : preis.betrag()).toList());
			if (waehrung == null) {
				waehrung = erste(anfrage.einmalkosten() == null ? null
						: anfrage.einmalkosten().stream().map(eintrag -> eintrag == null ? null : eintrag.betrag()).toList());
			}
			if (waehrung == null) {
				return true;
			}
			boolean gueltig = true;
			var steigerung = anfrage.preissteigerung();
			if (steigerung != null && abweichend(steigerung.betrag(), waehrung)) {
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
				if (eintrag != null && abweichend(eintrag.betrag(), waehrung)) {
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

		private static Currency erste(List<Betrag> betraege) {
			return betraege == null ? null : betraege.stream()
					.filter(betrag -> betrag != null && betrag.waehrung() != null)
					.map(Betrag::waehrung)
					.findFirst().orElse(null);
		}

		private static boolean abweichend(Betrag betrag, Currency waehrung) {
			return betrag != null && betrag.waehrung() != null && !betrag.waehrung().equals(waehrung);
		}

	}

}
