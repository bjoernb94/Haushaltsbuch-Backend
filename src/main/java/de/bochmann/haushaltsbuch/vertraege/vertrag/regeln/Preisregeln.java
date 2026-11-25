package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import jakarta.validation.ConstraintValidatorContext;

/**
 * Gemeinsames der Constraints an der Preisliste ({@link GueltigAbEindeutig} …) und am Vertrag über die Preise
 * ({@link PreisNichtVorBeginn}, {@link ErsterPreisAbBeginn}).
 */
final class Preisregeln {

	private Preisregeln() {
	}

	/**
	 * Hängt den Verstoß an ein Feld des Eintrags {@code index}, z.&nbsp;B. {@code preise[2].gueltigAb}, statt an die
	 * ganze Liste, damit die SPA die Zeile markieren kann.
	 *
	 * @return immer {@code false}, zum Zuweisen an das Ergebnis der Prüfung
	 */
	static boolean verstoss(ConstraintValidatorContext context, int index, String feld) {
		meldung(context).addPropertyNode(feld).inIterable().atIndex(index).addConstraintViolation();
		return false;
	}

	/**
	 * Wie {@link #verstoss(ConstraintValidatorContext, int, String)} für einen Constraint am ganzen Vertrag, also mit
	 * {@code preise} davor.
	 */
	static boolean verstossInPreisen(ConstraintValidatorContext context, int index, String feld) {
		meldung(context).addPropertyNode("preise").addPropertyNode(feld).inIterable().atIndex(index)
				.addConstraintViolation();
		return false;
	}

	/** Wie {@link #verstoss(ConstraintValidatorContext, int, String)}, eine Ebene tiefer, z.&nbsp;B. {@code betrag.waehrung}. */
	static boolean verstoss(ConstraintValidatorContext context, int index, String feld, String unterfeld) {
		meldung(context).addPropertyNode(feld).inIterable().atIndex(index).addPropertyNode(unterfeld)
				.addConstraintViolation();
		return false;
	}

	private static ConstraintValidatorContext.ConstraintViolationBuilder meldung(ConstraintValidatorContext context) {
		context.disableDefaultConstraintViolation();
		return context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate());
	}

}
