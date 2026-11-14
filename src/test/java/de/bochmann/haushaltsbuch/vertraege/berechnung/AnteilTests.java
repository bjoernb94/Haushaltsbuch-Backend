package de.bochmann.haushaltsbuch.vertraege.berechnung;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static de.bochmann.haushaltsbuch.shared.geld.Betrag.euro;
import static org.assertj.core.api.Assertions.assertThat;

class AnteilTests {

	@Test
	void ohneAngabeGanz() {
		assertThat(Anteil.aus(null)).isEqualTo(Anteil.GANZ);
		assertThat(Anteil.GANZ.geteilt()).isFalse();
		assertThat(Anteil.aus(new BigDecimal("100.0")).geteilt()).isFalse();
		assertThat(Anteil.aus(new BigDecimal("99.9")).geteilt()).isTrue();
	}

	@Test
	void rechnetUngerundet() {
		assertThat(Anteil.aus(new BigDecimal("33.3")).von(euro("10.01")).wert()).isEqualByComparingTo("3.33333");
		assertThat(Anteil.aus(new BigDecimal("50")).von(euro("10.01")).wert()).isEqualByComparingTo("5.005");
		assertThat(Anteil.GANZ.von(euro("10.01")).gerundet()).isEqualTo(euro("10.01"));
	}

}
