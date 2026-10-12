package io.github.noahhhx.mimos.recipes.recipe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Reads an image's format from its magic bytes, without decoding it: the
 * server never decodes a picture, browsers do.
 */
final class ImageSniffer {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageSniffer() {}

    static Optional<PictureFormat> detect(byte[] bytes) {
        if (startsWith(bytes, 0, JPEG)) {
            return Optional.of(PictureFormat.JPEG);
        }
        if (startsWith(bytes, 0, PNG)) {
            return Optional.of(PictureFormat.PNG);
        }
        if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP)) {
            return Optional.of(PictureFormat.WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
