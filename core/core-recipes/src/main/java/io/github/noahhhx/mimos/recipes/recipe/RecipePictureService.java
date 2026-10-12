package io.github.noahhhx.mimos.recipes.recipe;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe pictures (ADR-0023): the one owner of what a valid picture is and
 * who may add, read, or remove one. Ownership comes from
 * {@link RecipeService}, so a library recipe is read-only (403) and another
 * household's recipe is a 404, exactly as for the recipe itself. Bytes go
 * through the {@link PictureStore}; everything else stays relational.
 */
@Service
@Transactional
public class RecipePictureService {

    /** At most this many pictures per recipe. Mirrored by the web's MAX_PICTURES. */
    public static final int MAX_PICTURES = 8;

    /** At most this many bytes per picture. Mirrored by the web, and the API's multipart limit is built on it. */
    public static final int MAX_PICTURE_BYTES = 2 * 1024 * 1024;

    private final RecipeService recipes;
    private final RecipePictureRepository repository;
    private final PictureStore store;
    private final Clock clock;

    public RecipePictureService(
            RecipeService recipes, RecipePictureRepository repository, PictureStore store, Clock clock) {
        this.recipes = recipes;
        this.repository = repository;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Adds a picture after a personal recipe's others. The recipe's row is
     * locked first, so two members adding at once can neither pass the count
     * limit nor take the same position.
     */
    public RecipePicture add(UUID ownerId, UUID recipeId, byte[] bytes) {
        repository.lockRecipe(recipeId);
        recipes.requireOwned(ownerId, recipeId);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("a picture is required");
        }
        if (bytes.length > MAX_PICTURE_BYTES) {
            throw new IllegalArgumentException("a picture can be at most " + MAX_PICTURE_BYTES / (1024 * 1024) + " MB");
        }
        PictureFormat format = ImageSniffer.detect(bytes)
                .orElseThrow(() -> new IllegalArgumentException("a picture must be a JPEG, PNG, or WebP image"));
        if (repository.count(recipeId) >= MAX_PICTURES) {
            throw new IllegalArgumentException("a recipe can have at most " + MAX_PICTURES + " pictures");
        }
        RecipePicture picture = new RecipePicture(UUID.randomUUID(), format);
        repository.insert(recipeId, picture, clock.instant());
        store.put(picture.id(), bytes);
        return picture;
    }

    /** Removes a picture from a personal recipe; the others keep their order. */
    public void remove(UUID ownerId, UUID recipeId, UUID pictureId) {
        recipes.requireOwned(ownerId, recipeId);
        if (!repository.delete(recipeId, pictureId)) {
            throw new NoSuchElementException("picture not found: " + pictureId);
        }
    }

    /** A recipe's pictures in display order. The caller has already loaded a recipe it may see. */
    @Transactional(readOnly = true)
    public List<RecipePicture> list(UUID recipeId) {
        return repository.findByRecipe(recipeId);
    }

    /** The image of a picture of a recipe the viewer may see; a 404 for anything else. */
    @Transactional(readOnly = true)
    public PictureContent content(UUID ownerId, UUID recipeId, UUID pictureId) {
        recipes.findVisible(recipeId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        RecipePicture picture = repository
                .find(recipeId, pictureId)
                .orElseThrow(() -> new NoSuchElementException("picture not found: " + pictureId));
        return new PictureContent(picture.format(), store.get(picture.id()));
    }
}
