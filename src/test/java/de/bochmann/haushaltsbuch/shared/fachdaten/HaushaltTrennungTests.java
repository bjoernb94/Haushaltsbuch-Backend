package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.TestHaushalt;
import de.bochmann.haushaltsbuch.haushalt.HaushaltService;
import de.bochmann.haushaltsbuch.shared.Komponente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Trennung der Haushalte über {@code @TenantId} in {@link FachEntity}. */
@IntegrationTest
class HaushaltTrennungTests {

	@Autowired
	private FachEntityTestdatenRepository repository;

	@Autowired
	private HaushaltKontext haushaltKontext;

	@Autowired
	private HaushaltService haushaltService;

	@Autowired
	private JdbcClient jdbcClient;

	private final UUID haushaltA = UUID.randomUUID();

	private final UUID haushaltB = UUID.randomUUID();

	@AfterEach
	void aufraeumen() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void anlegenSetztHaushalt() {
		UUID id = HaushaltKontext.fuer(haushaltA, () -> repository.saveAndFlush(new FachEntityTestdaten("a"))).getId();

		assertThat(jdbcClient.sql("SELECT haushalt_id FROM test_fach_entity WHERE id = ?").param(id)
				.query(UUID.class).single()).isEqualTo(haushaltA);
	}

	@Test
	void fremderHaushaltSiehtNichts() {
		UUID id = HaushaltKontext.fuer(haushaltA, () -> repository.saveAndFlush(new FachEntityTestdaten("a"))).getId();
		HaushaltKontext.fuer(haushaltB, () -> repository.saveAndFlush(new FachEntityTestdaten("b")));

		assertThat(HaushaltKontext.fuer(haushaltB, () -> repository.findById(id))).isEmpty();
		assertThat(HaushaltKontext.fuer(haushaltB, () -> repository.existsById(id))).isFalse();
		assertThat(HaushaltKontext.fuer(haushaltB, () -> repository.findAll()))
				.extracting(daten -> daten.text).containsExactly("b");
		assertThat(HaushaltKontext.fuer(haushaltA, () -> repository.findById(id))).isPresent();
	}

	@Test
	void loeschenTrifftNurEigenenHaushalt() {
		UUID id = HaushaltKontext.fuer(haushaltA, () -> repository.saveAndFlush(new FachEntityTestdaten("a"))).getId();

		// Löschen über die ID lädt erst (gefiltert) und findet im fremden Haushalt nichts.
		HaushaltKontext.fuer(haushaltB, () -> {
			repository.deleteById(id);
			return null;
		});
		// Massenlöschen per JPQL ("delete from ...") ist ebenfalls gefiltert.
		HaushaltKontext.fuer(haushaltB, () -> {
			repository.deleteAllInBatch();
			return null;
		});

		assertThat(HaushaltKontext.fuer(haushaltA, () -> repository.findById(id))).isPresent();
	}

	@Test
	void ohneHaushaltNichtsLesenUndNichtsSpeichern() {
		HaushaltKontext.fuer(haushaltA, () -> repository.saveAndFlush(new FachEntityTestdaten("a")));

		assertThat(repository.findAll()).isEmpty();
		assertThatThrownBy(() -> repository.saveAndFlush(new FachEntityTestdaten("ohne")))
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(jdbcClient.sql("SELECT count(*) FROM test_fach_entity WHERE haushalt_id = ?")
				.param(HaushaltKontext.KEIN_HAUSHALT).query(Long.class).single()).isZero();
	}

	@Test
	void imRequestHaushaltDesAngemeldetenNutzers() {
		var mitglied = TestHaushalt.anlegen(haushaltService, Komponente.PORTFOLIO);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
		anmelden(mitglied.nutzerId());

		assertThat(haushaltKontext.id()).contains(mitglied.haushaltId());
		UUID id = repository.saveAndFlush(new FachEntityTestdaten("im request")).getId();
		assertThat(HaushaltKontext.fuer(mitglied.haushaltId(), () -> repository.findById(id))).isPresent();
	}

	@Test
	void nutzerOhneHaushaltHatKeinen() {
		anmelden(UUID.randomUUID());

		assertThat(haushaltKontext.id()).isEmpty();
	}

	private static void anmelden(UUID nutzerId) {
		var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(nutzerId.toString()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

}
