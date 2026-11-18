package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Lässt den gemockten Token-Endpunkt ({@link KeycloakTokenClientMock}) eine Anmeldung annehmen, für Tests außerhalb
 * dieses Pakets (z.&nbsp;B. die Registrierung, die danach anmeldet). Liegt hier, weil {@link KeycloakTokenClient}
 * package-private ist.
 */
public final class GemockteAnmeldung {

	private GemockteAnmeldung() {
	}

	/** Ab jetzt gibt der Token-Endpunkt für diese Anmeldedaten ein ID-Token mit {@code sub} = {@code id} zurück. */
	public static void nimmtAn(ApplicationContext context, String email, String passwort, UUID id) {
		var idToken = OidcIdToken.withTokenValue("id-token")
				.subject(id.toString())
				.claim("email", email)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300))
				.build();
		when(context.getBean(KeycloakTokenClient.class).anmelden(email, passwort))
				.thenReturn(new KeycloakTokenClient.Anmeldedaten(idToken,
						new KeycloakSitzung("rt", Instant.now().plusSeconds(300))));
	}

}
