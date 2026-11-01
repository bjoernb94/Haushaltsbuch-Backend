package de.bochmann.haushaltsbuch.shared.fachdaten;

import de.bochmann.haushaltsbuch.IntegrationTest;
import de.bochmann.haushaltsbuch.shared.fehler.KonfliktException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditing und Versionierung der {@link FachEntity} mit der Konfiguration der Anwendung. Alle
 * Zugriffe laufen für einen festen Haushalt; die Trennung selbst prüft {@link HaushaltTrennungTests}.
 */
@IntegrationTest
class FachEntityTests {

	@Autowired
	private FachEntityTestdatenRepository repository;

	@Autowired
	private TransactionOperations transaktion;

	private final UUID haushalt = UUID.randomUUID();

	@AfterEach
	void abmelden() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void fuelltAuditSpaltenBeimAnlegen() {
		UUID nutzer = anmelden();

		var gespeichert = imHaushalt(() -> repository.saveAndFlush(new FachEntityTestdaten("neu")));

		assertThat(gespeichert.getId()).isNotNull();
		assertThat(gespeichert.getVersion()).isZero();
		assertThat(gespeichert.getAngelegtAm()).isNotNull();
		assertThat(gespeichert.getAngelegtVon()).isEqualTo(nutzer);
		assertThat(gespeichert.getGeaendertAm()).isEqualTo(gespeichert.getAngelegtAm());
		assertThat(gespeichert.getGeaendertVon()).isEqualTo(nutzer);
	}

	@Test
	void ohneAnmeldungOhneNutzer() {
		var gespeichert = imHaushalt(() -> repository.saveAndFlush(new FachEntityTestdaten("job")));

		assertThat(gespeichert.getAngelegtAm()).isNotNull();
		assertThat(gespeichert.getAngelegtVon()).isNull();
	}

	@Test
	void aenderungErhoehtVersionUndMerktSichBearbeiter() {
		UUID anleger = anmelden();
		UUID id = imHaushalt(() -> repository.saveAndFlush(new FachEntityTestdaten("alt"))).getId();
		UUID bearbeiter = anmelden();

		imHaushalt(() -> transaktion.execute(status -> repository.findById(id).orElseThrow().text = "neu"));

		var geaendert = imHaushalt(() -> repository.findById(id).orElseThrow());
		assertThat(geaendert.getVersion()).isEqualTo(1);
		assertThat(geaendert.getAngelegtVon()).isEqualTo(anleger);
		assertThat(geaendert.getGeaendertVon()).isEqualTo(bearbeiter);
		assertThat(geaendert.getGeaendertAm()).isAfterOrEqualTo(geaendert.getAngelegtAm());
	}

	@Test
	void veralteteVersionVomClientIstKonflikt() {
		var gespeichert = imHaushalt(() -> repository.saveAndFlush(new FachEntityTestdaten("x")));

		gespeichert.pruefeVersion(0);
		assertThatThrownBy(() -> gespeichert.pruefeVersion(3)).isInstanceOf(KonfliktException.class);
	}

	@Test
	void gleichzeitigeAenderungIstKonflikt() {
		UUID id = imHaushalt(() -> repository.saveAndFlush(new FachEntityTestdaten("x"))).getId();
		var ersterStand = imHaushalt(() -> repository.findById(id).orElseThrow());
		var zweiterStand = imHaushalt(() -> repository.findById(id).orElseThrow());

		ersterStand.text = "erster";
		imHaushalt(() -> repository.saveAndFlush(ersterStand));
		zweiterStand.text = "zweiter";

		assertThatThrownBy(() -> imHaushalt(() -> repository.saveAndFlush(zweiterStand)))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private <T> T imHaushalt(Supplier<T> arbeit) {
		return HaushaltKontext.fuer(haushalt, arbeit);
	}

	private static UUID anmelden() {
		UUID id = UUID.randomUUID();
		var nutzer = new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("token").subject(id.toString()).build());
		SecurityContextHolder.getContext().setAuthentication(new OAuth2AuthenticationToken(nutzer, List.of(), "keycloak"));
		return id;
	}

}
