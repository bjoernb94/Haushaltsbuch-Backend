package de.bochmann.haushaltsbuch.vertraege.vertrag.regeln;

import de.bochmann.haushaltsbuch.shared.fehler.UngueltigeEingabeException;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Unterbrechungsart;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Verlaengerung;
import de.bochmann.haushaltsbuch.vertraege.berechnung.laufzeit.Zahlweise;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Beim Ändern gegen die gespeicherten Unterbrechungen: unterbrochen im März 2025, wieder ab 01.04.2025. */
class UnterbrechungsregelnTests {

	private static final List<Unterbrechung> MAERZ = List.of(new Unterbrechung(LocalDate.of(2025, 3, 1),
			LocalDate.of(2025, 3, 31), Unterbrechungsart.KUENDIGUNG));

	/** Nur die Felder, die die Regeln brauchen. */
	private record Angaben(LocalDate beginn, LocalDate laufzeitende, LocalDate gekuendigtZum)
			implements Berechnungsangaben {

		@Override
		public List<? extends Preisangabe> preise() {
			return List.of();
		}

		@Override
		public Zahlweise zahlweise() {
			return Zahlweise.MONATLICH;
		}

		@Override
		public BigDecimal anteilProzent() {
			return null;
		}

		@Override
		public List<? extends Einmalkostenangabe> einmalkosten() {
			return List.of();
		}

		@Override
		public Steigerungsangabe preissteigerung() {
			return null;
		}

		@Override
		public LocalDate preisgarantieBis() {
			return null;
		}

		@Override
		public Boolean kuendbar() {
			return true;
		}

		@Override
		public Verlaengerung verlaengerung() {
			return Verlaengerung.MONATLICH;
		}

		@Override
		public Integer kuendigungsfristMonate() {
			return 1;
		}

	}

	private static String code(Angaben angaben) {
		return assertThrows(UngueltigeEingabeException.class, () -> Unterbrechungsregeln.pruefe(angaben, MAERZ))
				.getCode();
	}

	@Test
	void passendeAngaben() {
		var angaben = new Angaben(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 1));

		assertThatCode(() -> Unterbrechungsregeln.pruefe(angaben, MAERZ)).doesNotThrowAnyException();
		assertThatCode(() -> Unterbrechungsregeln.pruefe(new Angaben(LocalDate.of(2025, 6, 1), null, null), List.of()))
				.doesNotThrowAnyException();
	}

	@Test
	void verstoesseMitDenCodesDerVorschau() {
		assertThat(code(new Angaben(LocalDate.of(2025, 3, 1), null, null))).isEqualTo("BeginnVorUnterbrechung");
		assertThat(code(new Angaben(LocalDate.of(2025, 1, 1), null, LocalDate.of(2025, 3, 31))))
				.isEqualTo("KuendigungNachUnterbrechung");
		assertThat(code(new Angaben(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31), null)))
				.isEqualTo("LaufzeitendeNachAktuellemBeginn");
	}

}
