package io.github.noahhhx.mimos.openfoodfacts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Open Food Facts' answers in this package's test resources, recorded from
 * the live API in October 2026 (ADR-0025). {@code kilojoules-only.json} is
 * {@code marmite.json} with its kcal removed.
 */
final class Fixtures {

    private Fixtures() {}

    static byte[] bytes(String name) {
        try (InputStream in =
                Objects.requireNonNull(Fixtures.class.getResourceAsStream(name), () -> "missing fixture " + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static OffJson.Product product(String name) {
        OffJson.Answer answer = OffJson.MAPPER.readValue(bytes(name), OffJson.Answer.class);
        return Objects.requireNonNull(answer.product(), () -> name + " has no product");
    }
}
