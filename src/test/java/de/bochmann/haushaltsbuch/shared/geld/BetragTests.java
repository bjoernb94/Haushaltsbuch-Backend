package de.bochmann.haushaltsbuch.shared.geld;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BetragTests {

	private static final Currency USD = Currency.getInstance("USD");

	@Test
	void ergaenztNachkommastellen() {
		assertThat(Betrag.euro("12.3").wert()).isEqualTo(new BigDecimal("12.30"));
		assertThat(Betrag.euro("12")).isEqualTo(Betrag.euro("12.00"));
	}

	@Test
	void rundetNichtStillschweigend() {
		// Mehr als zwei Nachkommastellen lehnt die Validierung ab (siehe BetragIntegrationTests).
		assertThat(Betrag.euro("12.345").wert()).isEqualTo(new BigDecimal("12.345"));
	}

	@Test
	void rechnet() {
		assertThat(Betrag.euro("10.00").plus(Betrag.euro("2.50"))).isEqualTo(Betrag.euro("12.50"));
		assertThat(Betrag.euro("10.00").minus(Betrag.euro("12.50"))).isEqualTo(Betrag.euro("-2.50"));
		assertThat(Betrag.euro("-2.50").negiert()).isEqualTo(Betrag.euro("2.50"));
		assertThat(Betrag.euro("-0.01").kleinerNull()).isTrue();
		assertThat(Betrag.nullIn(Betrag.EUR)).isEqualTo(Betrag.euro("0"));
	}

	@Test
	void rundetKaufmaennisch() {
		assertThat(Betrag.euro("10.00").mal(new BigDecimal("0.2345"))).isEqualTo(Betrag.euro("2.35"));
		assertThat(Betrag.euro("-10.00").mal(new BigDecimal("0.2345"))).isEqualTo(Betrag.euro("-2.35"));
		assertThat(Betrag.euro("10.00").mal(new BigDecimal("0.2344"))).isEqualTo(Betrag.euro("2.34"));
	}

	@Test
	void verrechnetKeineUnterschiedlichenWaehrungen() {
		assertThatThrownBy(() -> Betrag.euro("1.00").plus(new Betrag(BigDecimal.ONE, USD)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void wertUndWaehrungPflicht() {
		assertThatThrownBy(() -> new Betrag(null, Betrag.EUR)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Betrag(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
	}

}
