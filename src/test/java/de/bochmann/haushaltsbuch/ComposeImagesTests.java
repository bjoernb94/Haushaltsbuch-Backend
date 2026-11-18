package de.bochmann.haushaltsbuch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dev und Betriebspaket nutzen dieselben Images von PostgreSQL und Keycloak. Dependabot aktualisiert beide
 * Dateien im selben PR; das fängt auch Änderungen von Hand in nur einer Datei ab.
 */
class ComposeImagesTests {

	@ParameterizedTest
	@ValueSource(strings = { "postgres", "keycloak" })
	void betriebNutztDieImagesDerEntwicklung(String dienst) {
		assertThat(ComposeImages.image(ComposeImages.BETRIEB, dienst)).as(dienst)
				.isEqualTo(ComposeImages.image(ComposeImages.ENTWICKLUNG, dienst));
	}

}
