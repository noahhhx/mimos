package io.github.noahhhx.mimos.api.food;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays Open Food Facts' product read for the endpoint tests: a few
 * products in the shapes the live API answers with (ADR-0025), a 404 with
 * {@code status: 0} for any other barcode, and a forced status for outages.
 */
final class StubOpenFoodFacts {

    static final Map<String, String> PRODUCTS = Map.of(
            "3017620422003", """
                    {"code":"3017620422003","status":1,"status_verbose":"product found","product":{
                     "product_name":"Nutella","generic_name":"Pâte à tartiner aux noisettes et au cacao",
                     "brands":"Nutella, Ferrero","nutrition_data_per":"100g","serving_quantity_unit":"g",
                     "nutriments":{"energy-kcal_100g":539,"energy-kj_100g":2252,"energy_100g":2252,
                      "proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9,"sugars_100g":56.3}}}
                    """,
            "5449000000996", """
                    {"code":"5449000000996","status":1,"status_verbose":"product found","product":{
                     "product_name":"Coca-Cola Original","brands":"COCA-COLA SERVICES SA/NV",
                     "nutrition_data_per":"100g","serving_quantity":330,"serving_quantity_unit":"ml",
                     "nutriments":{"energy-kcal_100g":42.4,"energy-kj_100g":180,"energy_100g":180,
                      "proteins_100g":0,"carbohydrates_100g":10.6,"fat_100g":0}}}
                    """,
            "50184453", """
                    {"code":"50184453","status":1,"status_verbose":"product found","product":{
                     "product_name":"Yeast Extract","brands":"Marmite","nutrition_data_per":"100g",
                     "serving_quantity":8,"serving_quantity_unit":"g",
                     "nutriments":{"energy-kcal_100g":260,"energy_100g":1087,"proteins_100g":34,
                      "carbohydrates_100g":30}}}
                    """,
            "6111246950845", """
                    {"code":"6111246950845","status":1,"status_verbose":"product found","product":{
                     "product_name":"","generic_name":"","brands":"Jibal","nutrition_data_per":"100ml",
                     "nutriments":{"energy-kcal_100g":55,"proteins_100g":3,"fat_100g":3}}}
                    """);

    private final HttpServer server;
    private final AtomicInteger forcedStatus = new AtomicInteger();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    StubOpenFoodFacts() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            String barcode = path.substring(path.lastIndexOf('/') + 1);
            String product = PRODUCTS.get(barcode);
            int status = forcedStatus.get() != 0 ? forcedStatus.get() : product == null ? 404 : 200;
            String body = status == 200 && product != null
                    ? product
                    : status == 404
                            ? "{\"code\":\"" + barcode + "\",\"status\":0,\"status_verbose\":\"product not found\"}"
                            : "<html>" + status + "</html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void force(int status) {
        forcedStatus.set(status);
    }

    /** The paths of every request it answered, oldest first. */
    List<String> paths() {
        return List.copyOf(paths);
    }

    void reset() {
        forcedStatus.set(0);
        paths.clear();
    }
}
