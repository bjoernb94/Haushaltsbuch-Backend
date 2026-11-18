package de.bochmann.haushaltsbuch;

import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Images der Dienste aus den Compose-Dateien. Die Tests nehmen ihre Images aus {@code compose.yaml}, damit
 * dev und Tests dieselbe Version haben und Dependabot sie an einer Stelle aktualisiert
 * (docs/entwicklung.md, Build und Tests).
 */
public final class ComposeImages {

	/** Lokale Entwicklungsumgebung, Quelle der Versionen für die Tests. */
	public static final Path ENTWICKLUNG = Path.of("compose.yaml");

	/** Betriebspaket; seine Images von PostgreSQL und Keycloak müssen zu {@link #ENTWICKLUNG} passen. */
	public static final Path BETRIEB = Path.of("betrieb", "compose.yaml");

	private ComposeImages() {
	}

	/** Image des Dienstes aus {@code compose.yaml}, z.&nbsp;B. {@code postgres:18.6}. */
	public static DockerImageName ausEntwicklung(String dienst) {
		return DockerImageName.parse(image(ENTWICKLUNG, dienst));
	}

	@SuppressWarnings("unchecked")
	static String image(Path datei, String dienst) {
		Map<String, Object> compose;
		try (Reader reader = Files.newBufferedReader(datei)) {
			compose = new Yaml().load(reader);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Cannot read " + datei.toAbsolutePath() + ".", e);
		}
		Map<String, Object> dienste = (Map<String, Object>) compose.get("services");
		if (dienste.get(dienst) instanceof Map<?, ?> eintrag && eintrag.get("image") instanceof String image) {
			return image;
		}
		throw new IllegalStateException(datei + " has no image for service " + dienst + ".");
	}

}
