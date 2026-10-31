package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.ModuleTest;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ModuleTest
class HaushaltTests {

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private HaushaltMitgliedAbfrage haushaltAbfrage;

	@Autowired
	private KomponentenAbfrage komponentenAbfrage;

	@Autowired
	private HaushaltRepository haushaltRepository;

	@Test
	void legtHaushaltMitNutzerAlsEinzigemMitgliedAn() {
		UUID nutzerId = UUID.randomUUID();

		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));

		assertThat(haushaltRepository.findById(haushaltId)).hasValueSatisfying(haushalt -> {
			assertThat(haushalt.getMitglieder()).containsExactly(nutzerId);
			assertThat(haushalt.getKomponenten()).containsExactly(Komponente.PORTFOLIO);
			assertThat(haushalt.getName()).isNull();
			assertThat(haushalt.getAngelegtAm()).isNotNull();
		});
		assertThat(komponentenAbfrage.komponentenVon(nutzerId)).containsExactly(Komponente.PORTFOLIO);
	}

	@Test
	void nutzerGehoertHoechstensEinemHaushaltAn() {
		UUID nutzerId = UUID.randomUUID();
		haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));

		assertThatThrownBy(() -> haushaltService.anlegen(nutzerId, Set.of(Komponente.FINANZUEBERSICHT)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void haushaltDesMitglieds() {
		UUID nutzerId = UUID.randomUUID();
		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(Komponente.VERTRAGSUEBERSICHT));

		assertThat(haushaltAbfrage.haushaltVon(nutzerId)).contains(haushaltId);
	}

	@Test
	void ohneHaushaltLeer() {
		assertThat(haushaltAbfrage.haushaltVon(UUID.randomUUID())).isEmpty();
	}

}
