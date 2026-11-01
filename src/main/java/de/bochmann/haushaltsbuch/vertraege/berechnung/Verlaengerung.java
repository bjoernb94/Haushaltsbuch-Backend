package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Wie sich ein Vertrag nach dem Laufzeitende verlängert, wenn er nicht gekündigt wird. */
public enum Verlaengerung {

	/** Jeweils um ein Zahlungsintervall (z.&nbsp;B. Abos, die jederzeit zum Ende des bezahlten Zeitraums kündbar sind). */
	ZAHLUNGSINTERVALL,

	/** Um je einen Monat. */
	MONATLICH,

	/** Um je zwölf Monate. */
	ZWOELF_MONATE,

	/** Gar nicht, der Vertrag endet mit dem Laufzeitende. */
	KEINE

}
