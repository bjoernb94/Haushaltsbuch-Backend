package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.ModuleTest;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import de.bochmann.haushaltsbuch.shared.fehler.NichtGefundenException;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.List;
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

	@AfterEach
	void abmelden() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void legtHaushaltMitNutzerAlsEinzigemMitgliedAn() {
		UUID nutzerId = UUID.randomUUID();

		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));

		assertThat(haushaltRepository.findById(haushaltId)).hasValueSatisfying(haushalt -> {
			assertThat(haushalt.getMitglieder()).containsExactly(nutzerId);
			assertThat(haushalt.getKomponenten()).containsExactly(Komponente.PORTFOLIO);
			assertThat(haushalt.getName()).isNull();
			assertThat(haushalt.getAngelegtAm()).isNotNull();
			assertThat(haushalt.getAngelegtVon()).isEqualTo(nutzerId);
			assertThat(haushalt.getGeaendertAm()).isNotNull();
			assertThat(haushalt.getVersion()).isZero();
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

	@Test
	void aendertKomponentenUndErhoehtVersion() {
		UUID nutzerId = UUID.randomUUID();
		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));
		anmelden(nutzerId);

		var geaendert = haushaltService.komponentenAendern(nutzerId,
				Set.of(Komponente.FINANZUEBERSICHT, Komponente.VERTRAGSUEBERSICHT), 0);

		assertThat(geaendert.komponenten())
				.containsExactlyInAnyOrder(Komponente.FINANZUEBERSICHT, Komponente.VERTRAGSUEBERSICHT);
		assertThat(geaendert.version()).isEqualTo(1);
		assertThat(haushaltService.komponenten(nutzerId)).isEqualTo(geaendert);
		assertThat(komponentenAbfrage.komponentenVon(nutzerId))
				.containsExactlyInAnyOrder(Komponente.FINANZUEBERSICHT, Komponente.VERTRAGSUEBERSICHT);
		assertThat(haushaltRepository.findById(haushaltId)).hasValueSatisfying(haushalt -> {
			assertThat(haushalt.getGeaendertVon()).isEqualTo(nutzerId);
			assertThat(haushalt.getGeaendertAm()).isAfterOrEqualTo(haushalt.getAngelegtAm());
		});
	}

	@Test
	void gleicheAuswahlLaesstVersion() {
		UUID nutzerId = UUID.randomUUID();
		haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO, Komponente.FINANZUEBERSICHT));

		var unveraendert = haushaltService.komponentenAendern(nutzerId,
				Set.of(Komponente.FINANZUEBERSICHT, Komponente.PORTFOLIO), 0);

		assertThat(unveraendert.version()).isZero();
	}

	@Test
	void veralteteVersionKonflikt() {
		UUID nutzerId = UUID.randomUUID();
		haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));
		haushaltService.komponentenAendern(nutzerId, Set.of(Komponente.FINANZUEBERSICHT), 0);

		assertThatThrownBy(() -> haushaltService.komponentenAendern(nutzerId, Set.of(Komponente.VERTRAGSUEBERSICHT), 0))
				.isInstanceOf(KonfliktException.class);
		assertThat(komponentenAbfrage.komponentenVon(nutzerId)).containsExactly(Komponente.FINANZUEBERSICHT);
	}

	@Test
	void ohneKomponenteAbgelehnt() {
		UUID nutzerId = UUID.randomUUID();
		haushaltService.anlegen(nutzerId, Set.of(Komponente.PORTFOLIO));

		assertThatThrownBy(() -> haushaltService.komponentenAendern(nutzerId, Set.of(), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void komponentenOhneHaushaltNichtGefunden() {
		UUID nutzerId = UUID.randomUUID();

		assertThatThrownBy(() -> haushaltService.komponenten(nutzerId)).isInstanceOf(NichtGefundenException.class);
		assertThatThrownBy(() -> haushaltService.komponentenAendern(nutzerId, Set.of(Komponente.PORTFOLIO), 0))
				.isInstanceOf(NichtGefundenException.class);
	}

	private static void anmelden(UUID id) {
		var nutzer = new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("token").subject(id.toString()).build());
		SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(nutzer, List.of(), "keycloak"));
	}

}
