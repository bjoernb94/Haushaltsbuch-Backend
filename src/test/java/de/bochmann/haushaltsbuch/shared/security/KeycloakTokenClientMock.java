package de.bochmann.haushaltsbuch.shared.security;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ersetzt den Token-Endpunkt von Keycloak (Anmeldung, Erneuerung, Abmeldung) durch einen Mock.
 * Teil von {@link de.bochmann.haushaltsbuch.IntegrationTest}; liegt hier, weil
 * {@link KeycloakTokenClient} package-private ist. Tests im Paket holen sich den Mock per
 * {@code @Autowired}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(types = KeycloakTokenClient.class)
public @interface KeycloakTokenClientMock {

}
