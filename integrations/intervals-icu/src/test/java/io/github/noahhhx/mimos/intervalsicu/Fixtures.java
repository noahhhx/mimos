package io.github.noahhhx.mimos.intervalsicu;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/** The frozen intervals.icu responses in this package's test resources, shaped by its OpenAPI spec. */
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

    static <T> T read(String name, Class<T> type) {
        return IcuJson.MAPPER.readValue(bytes(name), type);
    }
}
