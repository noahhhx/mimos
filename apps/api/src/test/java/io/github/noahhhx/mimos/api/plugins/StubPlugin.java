package io.github.noahhhx.mimos.api.plugins;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * A real local HTTP server playing a registered plugin for the endpoint
 * tests (ADR-0006: fan-out runs against a real server). It offers both
 * capabilities. The suggestions and week-panel payloads are mutable per
 * test; the bodies Mimos sends are captured and fan-out calls are counted.
 */
final class StubPlugin implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<@Nullable String> lastContext = new AtomicReference<>();
    private final AtomicInteger suggestionCalls = new AtomicInteger();
    private final List<String> weekPanelRequests = new CopyOnWriteArrayList<>();
    volatile String suggestionsJson = "{\"suggestions\":[]}";
    volatile String weekPanelJson = "{\"blocks\":[]}";
    volatile int weekPanelStatus = 200;

    private StubPlugin(HttpServer server) {
        this.server = server;
    }

    static StubPlugin start() throws IOException {
        return start("stub-plugin", "Stub Plugin");
    }

    static StubPlugin start(String id, String name) throws IOException {
        String manifest = """
                {"schema":"mimos.plugin.manifest/v1","id":"%s","name":"%s","version":"1.0.0",\
                "apiVersions":["1"],"capabilities":["plan-suggestions","week-panel"]}\
                """.formatted(id, name);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        StubPlugin stub = new StubPlugin(server);
        server.createContext("/manifest", exchange -> respond(exchange, 200, manifest));
        server.createContext("/v1/plan-suggestions", exchange -> {
            stub.suggestionCalls.incrementAndGet();
            stub.lastContext.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, stub.suggestionsJson);
        });
        server.createContext("/v1/week-panel", exchange -> {
            stub.weekPanelRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, stub.weekPanelStatus, stub.weekPanelJson);
        });
        server.start();
        return stub;
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Nullable
    String lastContext() {
        return lastContext.get();
    }

    int suggestionCalls() {
        return suggestionCalls.get();
    }

    /** Every week-panel request body Mimos sent, oldest first. */
    List<String> weekPanelRequests() {
        return List.copyOf(weekPanelRequests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
