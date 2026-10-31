package de.bochmann.haushaltsbuch.nutzer;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ersetzt den Zugriff auf die Keycloak Admin API durch einen Mock. Teil von
 * {@link de.bochmann.haushaltsbuch.IntegrationTest}; liegt hier, weil {@link Identitaetsanbieter}
 * package-private ist. Tests im Paket holen sich den Mock per {@code @Autowired}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(types = Identitaetsanbieter.class)
public @interface IdentitaetsanbieterMock {

}
