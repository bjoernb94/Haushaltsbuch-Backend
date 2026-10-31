package de.bochmann.haushaltsbuch;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class ModularityTests {

	private static final Path DOKU = Path.of("docs/module");

	private final ApplicationModules modules = ApplicationModules.of(HaushaltsbuchBackendApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	/**
	 * Schreibt Diagramme (PlantUML) und Beschreibungen (AsciiDoc) der Module nach {@code docs/module}.
	 * Die Dateien sind eingecheckt; ändert sich die Modulstruktur, zeigt der Diff das mit an.
	 */
	@Test
	void writesDocumentation() throws IOException {
		new Documenter(modules, Documenter.Options.defaults().withOutputFolder(DOKU.toString()))
				.writeDocumentation();
		try (Stream<Path> dateien = Files.list(DOKU)) {
			for (Path datei : dateien.filter(d -> d.toString().endsWith(".puml")).toList()) {
				Files.write(datei, sortiert(Files.readAllLines(datei)));
			}
		}
	}

	/**
	 * Der Documenter gibt Module und Beziehungen in wechselnder Reihenfolge aus. Damit die eingecheckten
	 * Diagramme nur bei echten Änderungen einen Diff zeigen, werden zusammenhängende {@code Component(...)}-
	 * und {@code Rel(...)}-Zeilen sortiert; an der Darstellung ändert das nichts.
	 */
	private static List<String> sortiert(List<String> zeilen) {
		List<String> ergebnis = new ArrayList<>();
		List<String> block = new ArrayList<>();
		String blockArt = null;
		for (String zeile : zeilen) {
			String art = art(zeile);
			if (blockArt != null && !blockArt.equals(art)) {
				block.sort(null);
				ergebnis.addAll(block);
				block.clear();
			}
			blockArt = art;
			if (art == null) {
				ergebnis.add(zeile);
			}
			else {
				block.add(zeile);
			}
		}
		block.sort(null);
		ergebnis.addAll(block);
		return ergebnis;
	}

	private static String art(String zeile) {
		String ohneEinzug = zeile.strip();
		if (ohneEinzug.startsWith("Rel(")) {
			return "Rel";
		}
		if (ohneEinzug.startsWith("Component(")) {
			return "Component";
		}
		return null;
	}

}
