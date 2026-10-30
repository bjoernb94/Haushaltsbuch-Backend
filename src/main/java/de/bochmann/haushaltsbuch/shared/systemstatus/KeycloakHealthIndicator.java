package de.bochmann.haushaltsbuch.shared.systemstatus;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Prüft, ob der Keycloak-Realm erreichbar ist, über sein OIDC-Discovery-Dokument. Teil der
 * Health-Gruppe {@code systemstatus}, die die Login-Seite der SPA anzeigt.
 * <p>
 * Der Health-Endpunkt ist öffentlich; damit nicht jeder Aufruf eine Anfrage an Keycloak auslöst,
 * wird das Ergebnis kurz zwischengespeichert. Der Actuator-Cache greift bei Gruppen nicht.
 */
class KeycloakHealthIndicator implements HealthIndicator {

	static final Duration GUELTIGKEIT = Duration.ofSeconds(10);

	private final RestClient restClient;

	private final Clock clock;

	private volatile Ergebnis letztesErgebnis;

	private record Ergebnis(Health health, Instant zeitpunkt) {
	}

	/** @param restClient zeigt auf die Issuer-URI des Realms */
	KeycloakHealthIndicator(RestClient restClient, Clock clock) {
		this.restClient = restClient;
		this.clock = clock;
	}

	@Override
	public Health health() {
		Ergebnis ergebnis = letztesErgebnis;
		Instant jetzt = clock.instant();
		if (ergebnis == null || !jetzt.isBefore(ergebnis.zeitpunkt().plus(GUELTIGKEIT))) {
			ergebnis = new Ergebnis(pruefen(), jetzt);
			letztesErgebnis = ergebnis;
		}
		return ergebnis.health();
	}

	private Health pruefen() {
		try {
			restClient.get().uri("/.well-known/openid-configuration").retrieve().toBodilessEntity();
			return Health.up().build();
		}
		catch (RuntimeException e) {
			return Health.down(e).build();
		}
	}

}
