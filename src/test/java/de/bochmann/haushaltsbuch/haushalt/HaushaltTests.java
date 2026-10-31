package de.bochmann.haushaltsbuch.haushalt;

import de.bochmann.haushaltsbuch.TestcontainersConfiguration;
import de.bochmann.haushaltsbuch.shared.Komponente;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gleiche Konfiguration wie KomponentenControllerTests, damit beide denselben Kontext nutzen.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "keycloak"})
class HaushaltTests {

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private AktuellerHaushalt aktuellerHaushalt;

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
	void aktuellerHaushaltDesAngemeldetenNutzers() {
		UUID nutzerId = UUID.randomUUID();
		UUID haushaltId = haushaltService.anlegen(nutzerId, Set.of(Komponente.VERTRAGSUEBERSICHT));

		assertThat(aktuellerHaushalt.id(angemeldet(nutzerId))).isEqualTo(haushaltId);
	}

	@Test
	void ohneHaushaltVerboten() {
		assertThatThrownBy(() -> aktuellerHaushalt.id(angemeldet(UUID.randomUUID())))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
	}

	@Test
	void anonymNichtAngemeldet() {
		var anonym = new AnonymousAuthenticationToken("schluessel", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

		assertThatThrownBy(() -> aktuellerHaushalt.id(anonym))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
	}

	private static JwtAuthenticationToken angemeldet(UUID nutzerId) {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(nutzerId.toString()).build();
		return new JwtAuthenticationToken(jwt);
	}

}
