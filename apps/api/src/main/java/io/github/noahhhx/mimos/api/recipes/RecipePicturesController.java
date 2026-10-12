package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.recipes.recipe.PictureContent;
import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.openapitools.api.RecipePicturesApi;
import org.openapitools.model.RecipePicture;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recipe pictures (ADR-0023). The domain decides what a valid picture is;
 * this only moves bytes. A picture never changes once added, so its image
 * is cached for a year, privately, since it needs a token.
 */
@RestController
public class RecipePicturesController implements RecipePicturesApi {

    private static final CacheControl IMMUTABLE =
            CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final RecipePictureService pictures;
    private final CurrentUserService currentUser;

    public RecipePicturesController(RecipePictureService pictures, CurrentUserService currentUser) {
        this.pictures = pictures;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<RecipePicture> addRecipePicture(UUID recipeId, MultipartFile file) {
        UUID householdId = currentUser.requireProfile().householdId();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("the picture could not be read", e);
        }
        return ResponseEntity.status(201)
                .body(new RecipePicture()
                        .id(pictures.add(householdId, recipeId, bytes).id()));
    }

    @Override
    public ResponseEntity<Resource> getRecipePicture(UUID recipeId, UUID pictureId) {
        PictureContent content = pictures.content(currentUser.requireProfile().householdId(), recipeId, pictureId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.format().mediaType()))
                .contentLength(content.bytes().length)
                .cacheControl(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(content.bytes()));
    }

    @Override
    public ResponseEntity<Void> deleteRecipePicture(UUID recipeId, UUID pictureId) {
        pictures.remove(currentUser.requireProfile().householdId(), recipeId, pictureId);
        return ResponseEntity.noContent().build();
    }
}
