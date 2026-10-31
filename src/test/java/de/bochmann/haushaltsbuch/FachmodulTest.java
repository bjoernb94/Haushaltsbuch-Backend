package de.bochmann.haushaltsbuch;

import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltAbfrage;
import de.bochmann.haushaltsbuch.shared.fachdaten.HaushaltKontext;
import de.bochmann.haushaltsbuch.shared.security.KomponentenAbfrage;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ModuleTest} für ein Fachmodul (Verträge, Buchungen …), das Fach-Entities speichert.
 * <p>
 * {@code shared} braucht {@link HaushaltAbfrage} und {@link KomponentenAbfrage}, die das Modul
 * {@code haushalt} implementiert; hier sind beide Mocks und liefern nichts. Den Haushalt setzt der
 * Test daher ausdrücklich: {@code HaushaltKontext.fuer(haushaltId, () -> service.anlegen(...))}.
 * Die Transaktion muss darin beginnen ({@link HaushaltKontext#fuer}); die Testklasse oder -methode
 * darf deshalb nicht {@code @Transactional} sein.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ModuleTest
@MockitoBean(types = {HaushaltAbfrage.class, KomponentenAbfrage.class})
public @interface FachmodulTest {

}
