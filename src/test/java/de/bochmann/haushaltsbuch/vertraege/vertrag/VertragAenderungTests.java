package de.bochmann.haushaltsbuch.vertraege.vertrag;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VertragAenderungTests {

	/** Die Felder von {@link VertragAenderung} stehen doppelt; sie dürfen nicht auseinanderlaufen. */
	@Test
	void felderWieBeimAnlegenPlusVersion() {
		List<String> aenderung = beschreibung(VertragAenderung.class);

		assertThat(aenderung.getFirst()).startsWith("version java.lang.Long [@jakarta.validation.constraints.NotNull");
		assertThat(aenderung.subList(1, aenderung.size())).containsExactlyElementsOf(beschreibung(VertragAnfrage.class));
		assertThat(VertragAenderung.class.getAnnotations()).isEqualTo(VertragAnfrage.class.getAnnotations());
	}

	/** Je Feld Name, Typ und Annotationen (inkl. ihrer Attribute). */
	private static List<String> beschreibung(Class<? extends Record> record) {
		return Arrays.stream(record.getRecordComponents())
				.map(VertragAenderungTests::beschreibung)
				.toList();
	}

	/** Die Constraints landen (Target {@code METHOD}) auch am Accessor des Felds. */
	private static String beschreibung(RecordComponent feld) {
		return feld.getName() + " " + feld.getGenericType().getTypeName() + " "
				+ Arrays.toString(feld.getAccessor().getAnnotations());
	}

}
