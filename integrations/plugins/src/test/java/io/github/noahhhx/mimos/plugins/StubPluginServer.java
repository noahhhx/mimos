package io.github.noahhhx.mimos.plugins;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * A real local HTTP server playing a plugin (ADR-0006: fan-out is tested
 * against a real server, never a mocked HTTP client). Serves a mutable
 * manifest and suggestions payload and captures the last context body.
 */
final class StubPluginServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<@Nullable String> lastContext = new AtomicReference<>();
    volatile String manifestJson;
    volatile String suggestionsJson;

    private StubPluginServer(HttpServer server, String manifestJson, String suggestionsJson) {
        this.server = server;
        this.manifestJson = manifestJson;
        this.suggestionsJson = suggestionsJson;
    }

    static StubPluginServer start(String manifestJson, String suggestionsJson) throws IOException {
        StubPluginServer stub = create(manifestJson, suggestionsJson);
        stub.start();
        return stub;
    }

    /**
     * Created and bound but not serving — a plugin that has not started
     * yet. Connections hang until the caller's timeout, then {@link #start()}
     * brings it up on the same port.
     */
    static StubPluginServer create(String manifestJson, String suggestionsJson) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        StubPluginServer stub = new StubPluginServer(server, manifestJson, suggestionsJson);
        server.createContext("/manifest", exchange -> {
            byte[] body = stub.manifestJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/v1/plan-suggestions", exchange -> {
            stub.lastContext.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = stub.suggestionsJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        return stub;
    }

    /** The server's base URL (the port is bound from creation on). */
    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The last context body Mimos sent, or {@code null} before the first call. */
    @Nullable
    String lastContext() {
        return lastContext.get();
    }

    void start() {
        server.start();
    }

    @Override
    public void close() {
        try {
            server.stop(0);
        } catch (IllegalStateException neverStarted) {
            // Closing a server that was never started is fine.
        }
    }
}
