package de.bochmann.haushaltsbuch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kleiner HTTP-Server für Tests von Timeouts, die {@code MockRestServiceServer} nicht abbilden kann:
 * Pfade antworten sofort ({@link #antwortet}) oder erst nach {@link #VERZOEGERUNG} ({@link #haengt}).
 */
public final class LangsamerServer implements AutoCloseable {

	/** Timeouts der Clients im Test, deutlich kürzer als {@link #VERZOEGERUNG}. */
	public static final Duration TIMEOUT = Duration.ofMillis(200);

	public static final Duration VERZOEGERUNG = Duration.ofSeconds(5);

	private final HttpServer server;

	private final ExecutorService threads = Executors.newCachedThreadPool();

	public LangsamerServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.setExecutor(threads);
		server.start();
	}

	/** Request-Factory wie in der Anwendung (von Boot erkannt), mit {@link #TIMEOUT}. */
	public static ClientHttpRequestFactory requestFactory() {
		return ClientHttpRequestFactoryBuilder.detect()
				.build(HttpClientSettings.defaults().withTimeouts(TIMEOUT, TIMEOUT));
	}

	public String uri() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	public LangsamerServer antwortet(String pfad, String json) {
		server.createContext(pfad, exchange -> antworten(exchange, json));
		return this;
	}

	public LangsamerServer haengt(String pfad) {
		server.createContext(pfad, exchange -> {
			try {
				Thread.sleep(VERZOEGERUNG);
				antworten(exchange, "{}");
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				exchange.close();
			}
		});
		return this;
	}

	private static void antworten(HttpExchange exchange, String json) throws IOException {
		byte[] inhalt = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, inhalt.length);
		try (var body = exchange.getResponseBody()) {
			body.write(inhalt);
		}
	}

	@Override
	public void close() {
		server.stop(0);
		threads.shutdownNow();
	}

}
