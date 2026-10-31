package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Auditing und Versionierung der {@link FachEntity} mit der Konfiguration der Anwendung. */
@IntegrationTest
class FachEntityTests {

	@Autowired
	private FachEntityTestdatenRepository repository;

	@Autowired
	private TransactionOperations transaktion;

	@AfterEach
	void abmelden() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void fuelltAuditSpaltenBeimAnlegen() {
		UUID nutzer = anmelden();

		var gespeichert = repository.saveAndFlush(new FachEntityTestdaten("neu"));

		assertThat(gespeichert.getId()).isNotNull();
		assertThat(gespeichert.getVersion()).isZero();
		assertThat(gespeichert.getAngelegtAm()).isNotNull();
		assertThat(gespeichert.getAngelegtVon()).isEqualTo(nutzer);
		assertThat(gespeichert.getGeaendertAm()).isEqualTo(gespeichert.getAngelegtAm());
		assertThat(gespeichert.getGeaendertVon()).isEqualTo(nutzer);
	}

	@Test
	void ohneAnmeldungOhneNutzer() {
		var gespeichert = repository.saveAndFlush(new FachEntityTestdaten("job"));

		assertThat(gespeichert.getAngelegtAm()).isNotNull();
		assertThat(gespeichert.getAngelegtVon()).isNull();
	}

	@Test
	void aenderungErhoehtVersionUndMerktSichBearbeiter() {
		UUID anleger = anmelden();
		UUID id = repository.saveAndFlush(new FachEntityTestdaten("alt")).getId();
		UUID bearbeiter = anmelden();

		transaktion.executeWithoutResult(status -> repository.findById(id).orElseThrow().text = "neu");

		var geaendert = repository.findById(id).orElseThrow();
		assertThat(geaendert.getVersion()).isEqualTo(1);
		assertThat(geaendert.getAngelegtVon()).isEqualTo(anleger);
		assertThat(geaendert.getGeaendertVon()).isEqualTo(bearbeiter);
		assertThat(geaendert.getGeaendertAm()).isAfterOrEqualTo(geaendert.getAngelegtAm());
	}

	@Test
	void veralteteVersionVomClientIstKonflikt() {
		var gespeichert = repository.saveAndFlush(new FachEntityTestdaten("x"));

		gespeichert.pruefeVersion(0);
		assertThatThrownBy(() -> gespeichert.pruefeVersion(3)).isInstanceOf(KonfliktException.class);
	}

	@Test
	void gleichzeitigeAenderungIstKonflikt() {
		UUID id = repository.saveAndFlush(new FachEntityTestdaten("x")).getId();
		var ersterStand = repository.findById(id).orElseThrow();
		var zweiterStand = repository.findById(id).orElseThrow();

		ersterStand.text = "erster";
		repository.saveAndFlush(ersterStand);
		zweiterStand.text = "zweiter";

		assertThatThrownBy(() -> repository.saveAndFlush(zweiterStand))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private static UUID anmelden() {
		UUID id = UUID.randomUUID();
		var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(id.toString()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
		return id;
	}

}
