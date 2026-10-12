package io.github.noahhhx.mimos.intervalsicu;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A real local HTTP server playing intervals.icu: it answers every path
 * with the status, headers, and body set on it, and keeps the last
 * request it saw.
 */
final class StubIntervalsIcu implements AutoCloseable {

    record Request(String method, URI uri, @Nullable String authorization) {}

    private final HttpServer server;
    private volatile int status = 200;
    private volatile Map<String, String> headers = Map.of();
    private volatile byte[] body = "[]".getBytes();
    private volatile @Nullable Request lastRequest;

    private StubIntervalsIcu(HttpServer server) {
        this.server = server;
    }

    static StubIntervalsIcu start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        StubIntervalsIcu stub = new StubIntervalsIcu(server);
        server.createContext("/", exchange -> {
            stub.lastRequest = new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            stub.headers.forEach(exchange.getResponseHeaders()::set);
            byte[] response = stub.body;
            exchange.sendResponseHeaders(stub.status, response.length == 0 ? -1 : response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        return stub;
    }

    void answer(int status, byte[] body, Map<String, String> headers) {
        this.status = status;
        this.body = body;
        this.headers = headers;
    }

    URI url() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @Nullable
    Request lastRequest() {
        return lastRequest;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
