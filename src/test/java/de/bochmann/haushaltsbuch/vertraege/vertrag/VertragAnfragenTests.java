package de.bochmann.haushaltsbuch.vertraege.vertrag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Die Anfragen der Verträge (Anlegen, Ändern, Vorschau) haben dieselben Felder und Prüfungen. */
class VertragAnfragenTests {

	/** Die Felder von {@link VertragAenderung} stehen doppelt; sie dürfen nicht auseinanderlaufen. */
	@Test
	void felderWieBeimAnlegenPlusVersion() {
		List<String> aenderung = beschreibung(VertragAenderung.class);

		assertThat(aenderung.getFirst()).startsWith("version java.lang.Long [@jakarta.validation.constraints.NotNull");
		assertThat(aenderung.subList(1, aenderung.size())).containsExactlyElementsOf(beschreibung(VertragAnfrage.class));
		assertThat(VertragAenderung.class.getAnnotations()).isEqualTo(VertragAnfrage.class.getAnnotations());
	}

	/**
	 * Die Vorschau prüft ihre Felder wie das Anlegen, nur ohne Pflicht ({@code NotNull}, {@code NotEmpty},
	 * {@code NotBlank}) außer bei {@code kuendbar}; sonst zeigte sie Werte, die sich nicht speichern lassen. Für die
	 * Zeilen (Preise, Einmalkosten, Preissteigerung) gilt dasselbe mit eigenen Typen der Vorschau.
	 */
	@Test
	void vorschauPrueftWieBeimAnlegen() throws Exception {
		pruefeWieBeimAnlegen(VertragVorschauAnfrage.class, VertragAnfrage.class);
		pruefeWieBeimAnlegen(VertragVorschauPreis.class, VertragPreisAnfrage.class);
		pruefeWieBeimAnlegen(VertragVorschauEinmalkosten.class, VertragEinmalkostenAnfrage.class);
		pruefeWieBeimAnlegen(VertragVorschauPreissteigerung.class, VertragPreissteigerungAnfrage.class);
		assertThat(VertragVorschauAnfrage.class.getAnnotations()).isEqualTo(VertragAnfrage.class.getAnnotations());
		// Eine Regel ohne passenden Wert ist in der Vorschau nur unvollständig, nicht ungültig.
		assertThat(VertragVorschauPreissteigerung.class.getAnnotations()).isEmpty();
		assertThat(VertragPreissteigerungAnfrage.class.getAnnotations()).singleElement().isInstanceOf(PassenderWert.class);
	}

	/** Die Zeilentypen der Vorschau statt der des Anlegens, für den Vergleich der Feldtypen. */
	private static final Map<String, String> ZEILEN = Map.of(
			VertragPreisAnfrage.class.getName(), VertragVorschauPreis.class.getName(),
			VertragEinmalkostenAnfrage.class.getName(), VertragVorschauEinmalkosten.class.getName(),
			VertragPreissteigerungAnfrage.class.getName(), VertragVorschauPreissteigerung.class.getName());

	private static void pruefeWieBeimAnlegen(Class<? extends Record> vorschau, Class<? extends Record> anlegen)
			throws Exception {
		for (var feld : vorschau.getRecordComponents()) {
			var original = anlegen.getMethod(feld.getName());
			var erwartet = Arrays.stream(original.getAnnotations())
					.filter(a -> feld.getName().equals("kuendbar") || !PFLICHT.contains(a.annotationType()))
					.toList();
			String typ = original.getGenericReturnType().getTypeName();
			for (var zeile : ZEILEN.entrySet()) {
				typ = typ.replace(zeile.getKey(), zeile.getValue());
			}

			assertThat(feld.getGenericType().getTypeName()).as(feld.getName()).isEqualTo(typ);
			assertThat(feld.getAccessor().getAnnotations()).as(feld.getName()).containsExactlyElementsOf(erwartet);
		}
	}

	private static final Set<Class<?>> PFLICHT = Set.of(NotNull.class, NotEmpty.class, NotBlank.class);

	/** Je Feld Name, Typ und Annotationen (inkl. ihrer Attribute). */
	private static List<String> beschreibung(Class<? extends Record> record) {
		return Arrays.stream(record.getRecordComponents())
				.map(VertragAnfragenTests::beschreibung)
				.toList();
	}

	/** Die Constraints landen (Target {@code METHOD}) auch am Accessor des Felds. */
	private static String beschreibung(RecordComponent feld) {
		return feld.getName() + " " + feld.getGenericType().getTypeName() + " "
				+ Arrays.toString(feld.getAccessor().getAnnotations());
	}

}
