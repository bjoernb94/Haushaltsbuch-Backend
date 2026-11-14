package de.bochmann.haushaltsbuch.vertraege.berechnung;

/** Wie oft eine {@link Preissteigerung} den Preis erhöht. */
public enum Erhoehungsrhythmus {

	VIERTELJAEHRLICH(3),
	HALBJAEHRLICH(6),
	JAEHRLICH(12),
	ZWEIJAEHRLICH(24),
	DREIJAEHRLICH(36);

	private final int monate;

	Erhoehungsrhythmus(int monate) {
		this.monate = monate;
	}

	/** Abstand zwischen zwei Erhöhungen in Monaten. */
	public int monate() {
		return monate;
	}

}
