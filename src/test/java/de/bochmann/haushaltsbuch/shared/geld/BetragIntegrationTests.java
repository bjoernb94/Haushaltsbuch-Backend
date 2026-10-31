package de.bochmann.haushaltsbuch.shared.geld;

import de.bochmann.haushaltsbuch.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** {@link Betrag} mit der Konfiguration der Anwendung: JSON, Validierung, Datenbank. */
@IntegrationTest
class BetragIntegrationTests {

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private Validator validator;

	@Autowired
	private BetragTestdatenRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionOperations transaktion;

	record Anfrage(@NotNull @Valid Betrag betrag) {
	}

	@Test
	void jsonMitWertAlsZahl() {
		assertThat(jsonMapper.writeValueAsString(Betrag.euro("12.3")))
				.isEqualTo("{\"wert\":12.30,\"waehrung\":\"EUR\"}");
		assertThat(jsonMapper.readValue("{\"wert\": 0.1, \"waehrung\": \"EUR\"}", Betrag.class))
				.isEqualTo(Betrag.euro("0.10"));
	}

	@Test
	void jsonOhneWertNichtLesbar() {
		// Wird zu 400 ANFRAGE_UNGUELTIG; ein fehlender Betrag insgesamt fällt unter @NotNull.
		assertThatThrownBy(() -> jsonMapper.readValue("{\"waehrung\": \"EUR\"}", Betrag.class))
				.isInstanceOf(DatabindException.class);
	}

	@Test
	void gueltigerBetrag() {
		assertThat(validator.validate(new Anfrage(Betrag.euro("-12345678901234567.89")))).isEmpty();
	}

	@Test
	void zuVieleNachkommastellenUngueltig() {
		assertThat(validator.validate(new Anfrage(Betrag.euro("12.345"))))
				.extracting(v -> v.getPropertyPath().toString(),
						v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.containsExactly(tuple("betrag.wert", "Digits"));
	}

	@Test
	void andereWaehrungVorerstUngueltig() {
		var usd = new Betrag(BigDecimal.ONE, Currency.getInstance("USD"));
		assertThat(validator.validate(new Anfrage(usd)))
				.extracting(v -> v.getPropertyPath().toString(),
						v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.containsExactly(tuple("betrag.waehrung", "ErlaubteWaehrung"));
	}

	@Test
	void speichertJeBetragWertUndWaehrung() {
		var id = repository.save(new BetragTestdaten(Betrag.euro("-9.99"), null)).id;

		Map<String, Object> zeile = jdbcTemplate.queryForMap("select * from test_betrag where id = ?", id);
		assertThat(zeile).containsEntry("preis_wert", new BigDecimal("-9.99"))
				.containsEntry("preis_waehrung", "EUR")
				.containsEntry("gebuehr_wert", null);
		transaktion.executeWithoutResult(status -> {
			var gelesen = repository.findById(id).orElseThrow();
			assertThat(gelesen.preis).isEqualTo(Betrag.euro("-9.99"));
			assertThat(gelesen.gebuehr).isNull();
		});
	}

}
