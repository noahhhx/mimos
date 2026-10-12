package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Multipart limits for picture uploads (ADR-0023), built from the domain's
 * limit so the number lives in one place. The request may carry a little
 * more than the file for the multipart framing. Uploads stay in memory:
 * they are small, and the container needs no writable temp directory.
 */
@Configuration
class PictureUploadConfig {

    private static final int FRAMING_BYTES = 64 * 1024;

    @Bean
    MultipartConfigElement multipartConfigElement() {
        int maxRequestBytes = RecipePictureService.MAX_PICTURE_BYTES + FRAMING_BYTES;
        return new MultipartConfigElement("", RecipePictureService.MAX_PICTURE_BYTES, maxRequestBytes, maxRequestBytes);
    }
}
