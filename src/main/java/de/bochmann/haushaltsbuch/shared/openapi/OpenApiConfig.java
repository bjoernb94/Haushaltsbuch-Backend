package de.bochmann.haushaltsbuch.shared.openapi;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ergänzt die von springdoc erzeugte OpenAPI-Beschreibung um das, was sich nicht aus den Controllern
 * ablesen lässt: Anmeldung per Session-Cookie und CSRF-Header, die allgemeinen Fehlerantworten
 * ({@code ProblemDetail} mit {@code code}) und die Abmeldung, die die Security-Filterkette
 * bereitstellt. Eingecheckt als {@code docs/openapi.json} (siehe {@code OpenApiExportTests}).
 * <p>
 * Damit sich aus der Datei Typen erzeugen lassen: Felder mit {@code @Nullable} beschreibt springdoc als
 * nullable ({@code application.yml}), Enums als eigene Schemas, und in Antworten ist jedes Feld Pflicht.
 * <p>
 * Die einzelnen Fehlercodes stehen nur in {@code docs/api.md}, nicht in der OpenAPI-Beschreibung.
 * Öffentliche Endpunkte markiert der Controller mit {@code @SecurityRequirements} ohne Inhalt.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String SESSION = "session";

	static final String CSRF = "csrf";

	static final String PROBLEM = "Problem";

	private static final String FELDFEHLER = "Feldfehler";

	private static final String DOKU = "https://github.com/bjoernb94/Haushaltsbuch-Backend/blob/main/docs/api.md";

	static {
		// Jackson schreibt Currency als ISO-Code ("EUR"), nicht als Objekt (Betrag.waehrung).
		SpringDocUtils.getConfig().replaceWithClass(Currency.class, String.class);
		// Eigene Schemas statt inline, damit die SPA benannte Typen bekommt; zentral, damit die Enums frei von Swagger bleiben.
		ModelResolver.enumsAsRef = true;
	}

	@Bean
	OpenApiCustomizer haushaltsbuchApi() {
		return openApi -> {
			openApi.info(new Info()
					.title("Haushaltsbuch-Backend")
					// Version des API-Vertrags, nicht des Builds; Änderungen zeigt der Diff von docs/openapi.json.
					.version("1")
					.description("API für die SPA. Anmeldung, Fehlercodes und Datenformate: " + DOKU));
			// Relativ statt der URL der Anfrage, damit die eingecheckte Datei nicht vom Testlauf abhängt.
			openApi.servers(List.of(new Server().url("/")));
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			komponenten(openApi.getComponents());
			abmeldung(openApi);
			antwortfelderPflicht(openApi);
			openApi.getPaths().forEach((pfad, eintrag) -> eintrag.readOperationsMap()
					.forEach((methode, operation) -> ergaenzen(pfad, methode, operation)));
		};
	}

	private static void komponenten(Components components) {
		components.addSecuritySchemes(SESSION, new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.COOKIE)
				.name("JSESSIONID")
				.description("Session-Cookie, gesetzt von POST /api/anmeldung bzw. POST /api/registrierung."));
		components.addSecuritySchemes(CSRF, new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name("X-XSRF-TOKEN")
				.description("Wert des Cookies XSRF-TOKEN; Pflicht bei jeder schreibenden Anfrage."));
		components.addSchemas(FELDFEHLER, objekt(eigenschaften(
				"feld", typ("string", "null").description("Feld, z. B. passwort oder preis.wert; leer bei Regeln ohne Feld."),
				"code", typ("string").description("Name der Regel, z. B. NotBlank, Size, Unbekannt."),
				"parameter", typ("object").additionalProperties(true).description("Gesetzte Attribute der Regel, z. B. {\"min\": 8}.")))
				.required(List.of("code", "parameter")));
		components.addSchemas(PROBLEM, objekt(eigenschaften(
				"type", typ("string"),
				"title", typ("string"),
				"status", typ("integer"),
				"detail", typ("string").description("Englisch, nur für Entwickler und Logs."),
				"instance", typ("string"),
				"code", typ("string").description("Stabiler Fehlercode, z. B. VALIDIERUNG oder NICHT_ANGEMELDET; "
						+ "Tabelle in docs/api.md, Abschnitt Fehlerantworten."),
				"felder", typ("array").items(new Schema<>().$ref(FELDFEHLER))
						.description("Nur bei VALIDIERUNG: je Verstoß ein Eintrag.")))
				.required(List.of("status", "code")));
	}

	/**
	 * {@code POST /api/abmeldung} stellt die Security-Filterkette bereit, nicht ein Controller. Der
	 * Logout-Filter läuft vor der Zugriffsprüfung: Ohne Anmeldung kommt ebenfalls 204, daher wie ein
	 * öffentlicher Endpunkt beschrieben (nur CSRF-Token).
	 */
	private static void abmeldung(OpenAPI openApi) {
		openApi.getPaths().addPathItem("/api/abmeldung", new PathItem().post(new Operation()
				.tags(List.of("Anmeldung"))
				.operationId("abmelden")
				.summary("Beendet die Session im Backend und die Sitzung bei Keycloak; ohne Anmeldung ebenfalls 204.")
				.security(List.of())
				.responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("No Content")))));
	}

	/**
	 * Das Backend liefert immer alle Felder, auch die mit {@code null}. Daher ist in Erfolgsantworten jedes
	 * Feld Pflicht, sonst würde die SPA neben {@code null} auch noch „fehlt“ behandeln müssen. Vor
	 * {@link #ergaenzen} und nur bei 2xx, denn bei {@code Problem} dürfen Felder wirklich fehlen.
	 */
	private static void antwortfelderPflicht(OpenAPI openApi) {
		Set<Schema> gesehen = new HashSet<>();
		openApi.getPaths().values().forEach(eintrag -> eintrag.readOperations().forEach(operation ->
				operation.getResponses().forEach((status, antwort) -> {
					if (status.startsWith("2") && antwort.getContent() != null) {
						antwort.getContent().values().forEach(inhalt ->
								allePflicht(inhalt.getSchema(), openApi.getComponents(), gesehen));
					}
				})));
	}

	private static void allePflicht(Schema<?> schema, Components components, Set<Schema> gesehen) {
		if (schema == null) {
			return;
		}
		if (schema.get$ref() != null) {
			String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
			allePflicht(components.getSchemas().get(name), components, gesehen);
			return;
		}
		if (!gesehen.add(schema)) {
			return;
		}
		allePflicht(schema.getItems(), components, gesehen);
		if (schema.getOneOf() != null) {
			schema.getOneOf().forEach(variante -> allePflicht(variante, components, gesehen));
		}
		if (schema.getProperties() != null) {
			schema.setRequired(schema.getProperties().keySet().stream().sorted().toList());
			schema.getProperties().values().forEach(feld -> allePflicht(feld, components, gesehen));
		}
	}

	/**
	 * Anmeldung und allgemeine Fehlerantworten je Operation. Antworten, die die Operation schon selbst
	 * beschreibt, bleiben unverändert.
	 */
	private static void ergaenzen(String pfad, PathItem.HttpMethod methode, Operation operation) {
		boolean oeffentlich = operation.getSecurity() != null && operation.getSecurity().isEmpty();
		boolean schreibend = methode != PathItem.HttpMethod.GET && methode != PathItem.HttpMethod.HEAD
				&& methode != PathItem.HttpMethod.OPTIONS;

		var anmeldung = new SecurityRequirement();
		if (!oeffentlich) {
			anmeldung.addList(SESSION);
		}
		if (schreibend) {
			anmeldung.addList(CSRF);
		}
		operation.setSecurity(anmeldung.isEmpty() ? List.of() : List.of(anmeldung));

		ApiResponses antworten = operation.getResponses();
		boolean mitId = pfad.contains("{");
		if (operation.getRequestBody() != null) {
			fehler(antworten, "400", mitId
					? "Ungültige Anfrage (VALIDIERUNG mit felder, ANFRAGE_UNGUELTIG, z. B. ID kein UUID-Format)."
					: "Ungültige Anfrage (VALIDIERUNG mit felder, ANFRAGE_UNGUELTIG).");
		}
		else if (mitId) {
			fehler(antworten, "400", "Ungültige Anfrage, z. B. ID kein UUID-Format (ANFRAGE_UNGUELTIG).");
		}
		if (!oeffentlich) {
			fehler(antworten, "401", "Nicht angemeldet (NICHT_ANGEMELDET).");
		}
		if (!oeffentlich && schreibend) {
			fehler(antworten, "403", "Keine Berechtigung, z. B. Komponente nicht gebucht (KEIN_ZUGRIFF), "
					+ "oder CSRF-Token fehlt (CSRF_TOKEN_UNGUELTIG).");
		}
		else if (!oeffentlich) {
			fehler(antworten, "403", "Keine Berechtigung, z. B. Komponente nicht gebucht (KEIN_ZUGRIFF).");
		}
		else if (schreibend) {
			fehler(antworten, "403", "CSRF-Token fehlt (CSRF_TOKEN_UNGUELTIG).");
		}
		if (mitId) {
			fehler(antworten, "404", "Nicht gefunden, auch Daten eines fremden Haushalts (NICHT_GEFUNDEN).");
		}
		if (methode == PathItem.HttpMethod.PUT || methode == PathItem.HttpMethod.DELETE) {
			fehler(antworten, "409", "Inzwischen von jemand anderem geändert, version veraltet (KONFLIKT).");
		}
	}

	private static void fehler(ApiResponses antworten, String status, String beschreibung) {
		if (antworten.containsKey(status)) {
			return;
		}
		antworten.addApiResponse(status, new ApiResponse()
				.description(beschreibung)
				.content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
						new MediaType().schema(new Schema<>().$ref(PROBLEM)))));
	}

	private static Map<String, Schema> eigenschaften(Object... nameUndSchema) {
		Map<String, Schema> eigenschaften = new TreeMap<>();
		for (int i = 0; i < nameUndSchema.length; i += 2) {
			eigenschaften.put((String) nameUndSchema[i], (Schema<?>) nameUndSchema[i + 1]);
		}
		return eigenschaften;
	}

	private static Schema<Object> objekt(Map<String, Schema> eigenschaften) {
		return typ("object").properties(eigenschaften);
	}

	/** OpenAPI 3.1: Typen als Liste, z.&nbsp;B. {@code ["string", "null"]} für optionale Werte. */
	private static Schema<Object> typ(String... typen) {
		var schema = new Schema<Object>();
		for (String typ : typen) {
			schema.addType(typ);
		}
		return schema;
	}

}
