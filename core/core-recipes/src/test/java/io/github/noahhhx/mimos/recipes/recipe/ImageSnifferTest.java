package io.github.noahhhx.mimos.recipes.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageSnifferTest {

    @Test
    void readsJpegPngAndWebpFromTheirMagicBytes() {
        assertThat(ImageSniffer.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)))
                .contains(PictureFormat.JPEG);
        assertThat(ImageSniffer.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
                .contains(PictureFormat.PNG);
        assertThat(ImageSniffer.detect(ascii("RIFF$\0\0\0WEBPVP8 "))).contains(PictureFormat.WEBP);
    }

    @Test
    void rejectsOtherFormats() {
        assertThat(ImageSniffer.detect(ascii("GIF89a\u0001\0\u0001\0"))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("\0\0\0\u0018ftypheic\0\0\0\0"))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("RIFF$\0\0\0WAVEfmt "))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"/>")))
                .isEmpty();
        assertThat(ImageSniffer.detect(ascii("just some text"))).isEmpty();
    }

    @Test
    void rejectsEmptyAndTruncatedBytes() {
        assertThat(ImageSniffer.detect(new byte[0])).isEmpty();
        assertThat(ImageSniffer.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageSniffer.detect(bytes(0x89, 0x50, 0x4E, 0x47))).isEmpty();
        assertThat(ImageSniffer.detect(ascii("RIFF\0\0\0\0WEB"))).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
