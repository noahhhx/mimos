package io.github.noahhhx.mimos.api.support;

import static java.util.Objects.requireNonNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Real image bytes and picture uploads through the API (ADR-0023). */
public final class TestPictures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A 1×1 PNG. */
    public static final byte[] PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private TestPictures() {}

    /** A small JPEG, encoded by the JDK. */
    public static byte[] jpeg() {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xC07A2E);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Uploads bytes as the multipart part `file` and returns the status and body. */
    public static Upload upload(RestClient api, String token, UUID recipeId, byte[] bytes, MediaType partType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return "picture";
                    }
                })
                .contentType(partType);
        return requireNonNull(api.post()
                .uri("/api/v1/recipes/{recipeId}/pictures", recipeId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .exchange((request, response) -> new Upload(
                        response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes()))));
    }

    /** Uploads a picture that must be accepted and returns its id. */
    public static UUID add(RestClient api, String token, UUID recipeId, byte[] bytes) {
        Upload upload = upload(api, token, recipeId, bytes, MediaType.IMAGE_PNG);
        if (upload.status() != 201) {
            throw new AssertionError("upload answered " + upload.status() + ": " + upload.body());
        }
        return UUID.fromString(MAPPER.readTree(upload.body()).get("id").asText());
    }

    public record Upload(int status, String body) {}
}
