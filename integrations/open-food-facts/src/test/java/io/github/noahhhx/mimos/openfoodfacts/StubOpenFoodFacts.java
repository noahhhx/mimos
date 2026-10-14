package io.github.noahhhx.mimos.openfoodfacts;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * A real local HTTP server playing Open Food Facts: it answers every path
 * with the status and body set on it, and keeps the last request it saw.
 */
final class StubOpenFoodFacts implements AutoCloseable {

    record Request(String method, URI uri, @Nullable String userAgent) {}

    private final HttpServer server;
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];
    private volatile @Nullable Request lastRequest;

    private StubOpenFoodFacts(HttpServer server) {
        this.server = server;
    }

    static StubOpenFoodFacts start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        StubOpenFoodFacts stub = new StubOpenFoodFacts(server);
        server.createContext("/", exchange -> {
            stub.lastRequest = new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = stub.body;
            exchange.sendResponseHeaders(stub.status, response.length == 0 ? -1 : response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        return stub;
    }

    void answer(int status, byte[] body) {
        this.status = status;
        this.body = body;
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
