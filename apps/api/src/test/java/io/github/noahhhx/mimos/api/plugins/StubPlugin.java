package io.github.noahhhx.mimos.api.plugins;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * A real local HTTP server playing a registered plugin for the endpoint
 * tests (ADR-0006: fan-out runs against a real server). The suggestions
 * payload is mutable per test; the last context body is captured.
 */
final class StubPlugin implements AutoCloseable {

    static final String MANIFEST = """
            {"schema":"mimos.plugin.manifest/v1","id":"stub-plugin","name":"Stub Plugin",\
            "version":"1.0.0","apiVersions":["1"],"capabilities":["plan-suggestions"]}\
            """;

    private final HttpServer server;
    private final AtomicReference<@Nullable String> lastContext = new AtomicReference<>();
    volatile String suggestionsJson = "{\"suggestions\":[]}";

    private StubPlugin(HttpServer server) {
        this.server = server;
    }

    static StubPlugin start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        StubPlugin stub = new StubPlugin(server);
        server.createContext("/manifest", exchange -> {
            byte[] body = MANIFEST.getBytes(StandardCharsets.UTF_8);
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
        server.start();
        return stub;
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Nullable
    String lastContext() {
        return lastContext.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
