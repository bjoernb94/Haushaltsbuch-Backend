package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.FachmodulTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prüft, dass ein {@link FachmodulTest} die Infrastruktur aus {@code shared} lädt (Issue #44):
 * Auditing, Trennung der Haushalte und {@link Clock}; außerdem eine Testinstanz je Methode (#59).
 * Läuft im Modul {@code shared} selbst, damit es nicht von einem Fachmodul abhängt.
 */
@FachmodulTest
class FachmodulTestTests {

	@Autowired
	private FachEntityTestdatenRepository repository;

	/** Die Instanz des zuletzt gelaufenen Tests dieser Klasse. */
	private static Object vorherigeInstanz;

	@Autowired
	private Clock clock;

	/** Läuft in zwei Tests; egal in welcher Reihenfolge, der zweite sieht die Instanz des ersten. */
	@Test
	void eigeneInstanzJeTest() {
		pruefeEigeneInstanz();
	}

	@Test
	void eigeneInstanzJeTestAuchHier() {
		pruefeEigeneInstanz();
	}

	private void pruefeEigeneInstanz() {
		assertThat(this).isNotSameAs(vorherigeInstanz);
		vorherigeInstanz = this;
	}

	@Test
	void speichertMitAuditingImHaushalt() {
		UUID haushalt = UUID.randomUUID();

		var gespeichert = HaushaltKontext.fuer(haushalt,
				() -> repository.saveAndFlush(new FachEntityTestdaten("modultest")));

		assertThat(gespeichert.getHaushaltId()).isEqualTo(haushalt);
		assertThat(gespeichert.getAngelegtAm()).isNotNull().isBeforeOrEqualTo(clock.instant());
	}

	@Test
	void fremderHaushaltSiehtNichts() {
		UUID id = HaushaltKontext.fuer(UUID.randomUUID(),
				() -> repository.saveAndFlush(new FachEntityTestdaten("fremd"))).getId();

		Optional<FachEntityTestdaten> gefunden = HaushaltKontext.fuer(UUID.randomUUID(), () -> repository.findById(id));

		assertThat(gefunden).isEmpty();
	}

	@Test
	void ohneHaushaltKeinSpeichern() {
		assertThatThrownBy(() -> repository.saveAndFlush(new FachEntityTestdaten("ohne")))
				.hasRootCauseInstanceOf(IllegalStateException.class);
	}

}
