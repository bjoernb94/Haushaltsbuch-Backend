package de.bochmann.haushaltsbuch.shared.zeit;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PlausiblesDatumTests {

	private final PlausiblesDatum.Pruefung pruefung = new PlausiblesDatum.Pruefung();

	@Test
	void grenzenGehoerenDazu() {
		assertThat(pruefung.isValid(LocalDate.of(1900, 1, 1), null)).isTrue();
		assertThat(pruefung.isValid(LocalDate.of(2199, 12, 31), null)).isTrue();
		assertThat(pruefung.isValid(null, null)).isTrue();
	}

	@Test
	void ausserhalbUngueltig() {
		assertThat(pruefung.isValid(LocalDate.of(1899, 12, 31), null)).isFalse();
		assertThat(pruefung.isValid(LocalDate.of(2200, 1, 1), null)).isFalse();
		assertThat(pruefung.isValid(LocalDate.MAX, null)).isFalse();
	}

}
