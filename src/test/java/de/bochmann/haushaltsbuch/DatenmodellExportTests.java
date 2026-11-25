package de.bochmann.haushaltsbuch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schreibt das Datenmodell als Mermaid-{@code erDiagram} nach {@code docs/datenmodell.md}, aus dem
 * Schema, das die Flyway-Migrationen erzeugen. Die Datei ist eingecheckt wie {@code docs/module}:
 * Ändert sich das Schema, zeigt der Diff das, und die CI schlägt fehl, wenn sie nicht aktuell ist.
 * <p>
 * Ohne Spring-Kontext, in einer eigenen Datenbank nur mit den Migrationen aus {@code src/main}; die
 * Testmigrationen (ab Version 1000) liegen zwar auch unter {@code classpath:db/migration}, gehören aber
 * nicht zum Modell.
 */
class DatenmodellExportTests {

	private static final Path DATEI = Path.of("docs/datenmodell.md");

	/**
	 * Modul je Präfix des Tabellennamens. Eine Tabelle ohne passendes Präfix lässt den Test scheitern:
	 * dann hier das Modul eintragen.
	 */
	private static final Map<String, String> MODUL_JE_PRAEFIX = Map.of(
			"haushalt", "haushalt",
			"nutzer", "nutzer",
			"vertrag", "vertraege");

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrieren() {
		DataSource datenbank = TestcontainersConfiguration.neueDatenbank("datenmodell");
		Flyway.configure()
				.dataSource(datenbank)
				.locations("filesystem:src/main/resources/db/migration")
				.load()
				.migrate();
		jdbc = new JdbcTemplate(datenbank);
	}

	@Test
	void schreibtDatenmodell() throws IOException {
		Map<String, List<Spalte>> tabellen = tabellen();
		List<Beziehung> beziehungen = beziehungen(tabellen);
		Files.writeString(DATEI, markdown(tabellen, beziehungen));
	}

	@Test
	void jedeTabelleGehoertZuEinemModul() {
		assertThat(tabellen().keySet()).allSatisfy(tabelle -> assertThat(modul(tabelle))
				.as("Modul für Tabelle %s; Präfix in MODUL_JE_PRAEFIX eintragen", tabelle)
				.isNotNull());
	}

	/** Tabellen mit ihren Spalten in der Reihenfolge der Migrationen, Tabellen nach Name sortiert. */
	private static Map<String, List<Spalte>> tabellen() {
		Map<String, List<Spalte>> tabellen = new TreeMap<>();
		jdbc.query("""
				SELECT c.relname AS tabelle, a.attname AS spalte, format_type(a.atttypid, a.atttypmod) AS typ,
				       NOT a.attnotnull AS nullbar,
				       EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'p'
				               AND a.attnum = ANY (k.conkey)) AS pk,
				       EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = c.oid AND k.contype = 'f'
				               AND a.attnum = ANY (k.conkey)) AS fk,
				       EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.oid AND i.indisunique
				               AND NOT i.indisprimary AND i.indnkeyatts = 1 AND i.indkey[0] = a.attnum) AS uk
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
				WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
				ORDER BY c.relname, a.attnum
				""", zeile -> {
			tabellen.computeIfAbsent(zeile.getString("tabelle"), t -> new ArrayList<>()).add(new Spalte(
					zeile.getString("spalte"), zeile.getString("typ"), zeile.getBoolean("nullbar"),
					zeile.getBoolean("pk"), zeile.getBoolean("fk"), zeile.getBoolean("uk")));
		});
		return tabellen;
	}

	/**
	 * Fremdschlüssel (durchgezogen) und Verweise über Modulgrenzen ohne Fremdschlüssel nach ADR 0005
	 * (gestrichelt): eine Spalte {@code <x>_id} ohne Fremdschlüssel, wenn es die Tabelle {@code <x>} gibt.
	 */
	private static List<Beziehung> beziehungen(Map<String, List<Spalte>> tabellen) {
		List<Beziehung> beziehungen = new ArrayList<>();
		jdbc.query("""
				SELECT kind.relname AS tabelle, ziel.relname AS ziel,
				       array_to_string(ARRAY(SELECT a.attname FROM unnest(k.conkey) WITH ORDINALITY AS s(nr, pos)
				               JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = s.nr ORDER BY s.pos), ',') AS spalten
				FROM pg_constraint k
				JOIN pg_class kind ON kind.oid = k.conrelid
				JOIN pg_class ziel ON ziel.oid = k.confrelid
				WHERE k.contype = 'f'
				""", zeile -> {
			String tabelle = zeile.getString("tabelle");
			List<String> spalten = Arrays.asList(zeile.getString("spalten").split(","));
			beziehungen.add(new Beziehung(zeile.getString("ziel"), tabelle, spalten, true,
					spalten.stream().allMatch(s -> !spalte(tabellen, tabelle, s).nullbar()),
					eindeutig(tabelle, spalten)));
		});
		tabellen.forEach((tabelle, spalten) -> spalten.stream()
				.filter(spalte -> !spalte.fk() && spalte.name().endsWith("_id"))
				.forEach(spalte -> {
					String ziel = spalte.name().substring(0, spalte.name().length() - "_id".length());
					if (tabellen.containsKey(ziel) && !ziel.equals(tabelle)) {
						beziehungen.add(new Beziehung(ziel, tabelle, List.of(spalte.name()), false,
								!spalte.nullbar(), eindeutig(tabelle, List.of(spalte.name()))));
					}
				}));
		beziehungen.sort(Comparator.comparing(Beziehung::ziel).thenComparing(Beziehung::tabelle)
				.thenComparing(b -> String.join(",", b.spalten())));
		return beziehungen;
	}

	/** Ob die Spalten genau einen Primärschlüssel oder eindeutigen Index bilden (dann höchstens ein Datensatz je Ziel). */
	private static boolean eindeutig(String tabelle, List<String> spalten) {
		Set<String> gesucht = Set.copyOf(spalten);
		return jdbc.query("""
				SELECT array_to_string(ARRAY(SELECT a.attname FROM unnest(i.indkey) AS s(nr)
				        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = s.nr), ',') AS spalten
				FROM pg_index i JOIN pg_class c ON c.oid = i.indrelid
				WHERE c.relname = ? AND i.indisunique
				""", (zeile, nr) -> Set.of(zeile.getString("spalten").split(",")), tabelle)
				.contains(gesucht);
	}

	private static Spalte spalte(Map<String, List<Spalte>> tabellen, String tabelle, String name) {
		return tabellen.get(tabelle).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
	}

	private static String modul(String tabelle) {
		return MODUL_JE_PRAEFIX.entrySet().stream()
				.filter(e -> tabelle.equals(e.getKey()) || tabelle.startsWith(e.getKey() + "_"))
				.max(Comparator.comparingInt(e -> e.getKey().length()))
				.map(Map.Entry::getValue)
				.orElse(null);
	}

	private static String markdown(Map<String, List<Spalte>> tabellen, List<Beziehung> beziehungen) {
		var md = new StringBuilder("""
				# Datenmodell

				Erzeugt von `DatenmodellExportTests` aus dem Schema der Flyway-Migrationen
				(`src/main/resources/db/migration`), nicht von Hand ändern. Ändert sich das Schema, die Datei mit
				`./mvnw test -Dtest=DatenmodellExportTests` neu erzeugen und mit committen; die CI prüft, ob sie
				aktuell ist. Konventionen für das Schema stehen unter [Entwicklung](entwicklung.md#datenbank).

				- **Durchgezogene Linie:** Fremdschlüssel, nur innerhalb eines Moduls.
				- **Gestrichelte Linie:** Verweis über eine Modulgrenze ohne Fremdschlüssel
				  ([ADR 0005](entscheidungen/0005-keine-fremdschluessel-ueber-module.md)), erkannt an einer Spalte
				  `<tabelle>_id`. Audit-Spalten wie `angelegt_von` verweisen auch auf einen Nutzer, haben aber keine Linie.
				- `PK`, `FK`, `UK`: Primärschlüssel, Fremdschlüssel, eindeutig (nur einspaltig). `NULL`: Spalte darf leer sein.

				```mermaid
				erDiagram
				""");
		tabellen.forEach((tabelle, spalten) -> {
			md.append("    ").append(tabelle).append(" {\n");
			spalten.forEach(spalte -> md.append("        ").append(spalte.mermaid()).append('\n'));
			md.append("    }\n");
		});
		beziehungen.forEach(beziehung -> md.append("    ").append(beziehung.mermaid()).append('\n'));
		md.append("""
				```

				## Tabellen je Modul

				| Modul | Tabellen |
				|-------|----------|
				""");
		Map<String, List<String>> jeModul = tabellen.keySet().stream()
				.collect(Collectors.groupingBy(DatenmodellExportTests::modul, TreeMap::new, Collectors.toList()));
		jeModul.forEach((modul, namen) -> md.append("| `").append(modul).append("` | ")
				.append(namen.stream().map(n -> "`" + n + "`").collect(Collectors.joining(", "))).append(" |\n"));
		return md.toString();
	}

	record Spalte(String name, String sqlTyp, boolean nullbar, boolean pk, boolean fk, boolean uk) {

		/** {@code typ name PK, FK "Hinweis"}; Mermaid erlaubt im Typ keine Leerzeichen und Kommas. */
		String mermaid() {
			List<String> hinweise = new ArrayList<>();
			String typ = typ(hinweise);
			if (nullbar) {
				hinweise.add("NULL");
			}
			var schluessel = new LinkedHashMap<String, Boolean>();
			schluessel.put("PK", pk);
			schluessel.put("FK", fk);
			// Auch bei Teil eines zusammengesetzten Primärschlüssels: eigene Aussage (z. B. ein Haushalt je Nutzer).
			schluessel.put("UK", uk);
			String keys = schluessel.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
					.collect(Collectors.joining(", "));
			return typ + " " + name + (keys.isEmpty() ? "" : " " + keys)
					+ (hinweise.isEmpty() ? "" : " \"" + String.join(", ", hinweise) + "\"");
		}

		private String typ(List<String> hinweise) {
			if (sqlTyp.startsWith("numeric(")) {
				hinweise.add(sqlTyp.substring("numeric".length()));
				return "numeric";
			}
			return switch (sqlTyp) {
				case "timestamp with time zone" -> "timestamptz";
				case "timestamp without time zone" -> "timestamp";
				default -> sqlTyp.replace("character varying", "varchar").replace(' ', '_');
			};
		}

	}

	/**
	 * {@code ziel} ist die Tabelle, auf die verwiesen wird, {@code tabelle} die mit der Spalte. Mermaid:
	 * links das Ziel (genau eins oder keins), rechts beliebig viele oder höchstens einer.
	 */
	record Beziehung(String ziel, String tabelle, List<String> spalten, boolean fremdschluessel, boolean pflicht,
			boolean eindeutig) {

		String mermaid() {
			String links = pflicht ? "||" : "|o";
			String rechts = eindeutig ? "o|" : "o{";
			String linie = fremdschluessel ? "--" : "..";
			return ziel + " " + links + linie + rechts + " " + tabelle + " : \"" + String.join(", ", spalten) + "\"";
		}

	}

}
