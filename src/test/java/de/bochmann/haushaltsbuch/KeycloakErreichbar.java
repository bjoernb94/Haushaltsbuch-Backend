package de.bochmann.haushaltsbuch;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Lässt den gemockten Health-Indikator {@code keycloakHealthIndicator} vor jedem Test {@code UP}
 * melden; ein Mock ohne Vorgabe würde {@code null} liefern. Spring setzt Mocks nach jedem Test
 * zurück, daher vor jedem Test. Ein Test kann die Vorgabe mit eigenem {@code when(...)}
 * überschreiben.
 */
class KeycloakErreichbar implements BeforeEachCallback {

	@Override
	public void beforeEach(ExtensionContext context) {
		var indikator = SpringExtension.getApplicationContext(context)
				.getBean("keycloakHealthIndicator", HealthIndicator.class);
		when(indikator.health(anyBoolean())).thenReturn(Health.up().build());
	}

}
