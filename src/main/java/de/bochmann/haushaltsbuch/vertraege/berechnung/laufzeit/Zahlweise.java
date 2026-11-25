package de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit;

/** Wie oft der Betrag abgebucht wird. */
public enum Zahlweise {

	MONATLICH(1),
	VIERTELJAEHRLICH(3),
	HALBJAEHRLICH(6),
	JAEHRLICH(12);

	private final int monate;

	Zahlweise(int monate) {
		this.monate = monate;
	}

	/** Abstand zwischen zwei Abbuchungen in Monaten; teilt 12 ohne Rest. */
	public int monate() {
		return monate;
	}

}
