package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.recipes.collection.RecipeCollectionService;
import io.github.noahhhx.mimos.recipes.recipe.PictureContent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.RecipeCollectionsApi;
import org.openapitools.model.RecipeCollection;
import org.openapitools.model.RecipeCollectionInput;
import org.openapitools.model.RecipeSummary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RecipeCollectionsController implements RecipeCollectionsApi {

    private static final CacheControl IMMUTABLE =
            CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final RecipeCollectionService collections;
    private final CurrentUserService currentUser;
    private final RecipeSummaries summaries;

    public RecipeCollectionsController(
            RecipeCollectionService collections, CurrentUserService currentUser, RecipeSummaries summaries) {
        this.collections = collections;
        this.currentUser = currentUser;
        this.summaries = summaries;
    }

    @Override
    public ResponseEntity<List<RecipeCollection>> listRecipeCollections() {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(collections.list(profile.householdId(), profile.id()).stream()
                .map(RecipeApiMapper::toApiCollection)
                .toList());
    }

    @Override
    public ResponseEntity<RecipeCollection> createRecipeCollection(RecipeCollectionInput input) {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.status(201)
                .body(RecipeApiMapper.toApiCollection(collections.create(
                        profile.householdId(), profile.id(), RecipeApiMapper.toCollectionDraft(input))));
    }

    @Override
    public ResponseEntity<RecipeCollection> getRecipeCollection(UUID collectionId) {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(
                RecipeApiMapper.toApiCollection(collections.get(profile.householdId(), profile.id(), collectionId)));
    }

    @Override
    public ResponseEntity<RecipeCollection> replaceRecipeCollection(UUID collectionId, RecipeCollectionInput input) {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(RecipeApiMapper.toApiCollection(collections.replace(
                profile.householdId(), profile.id(), collectionId, RecipeApiMapper.toCollectionDraft(input))));
    }

    @Override
    public ResponseEntity<Void> deleteRecipeCollection(UUID collectionId) {
        collections.delete(currentUser.requireProfile().householdId(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listRecipeCollectionRecipes(UUID collectionId, @Nullable String q) {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(
                summaries.of(collections.members(profile.householdId(), collectionId, q), profile.id()));
    }

    @Override
    public ResponseEntity<Void> setDefaultRecipeCollection(UUID collectionId) {
        UserProfileRecord profile = currentUser.requireProfile();
        collections.setDefault(profile.householdId(), profile.id(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> clearDefaultRecipeCollection(UUID collectionId) {
        UserProfileRecord profile = currentUser.requireProfile();
        collections.clearDefault(profile.householdId(), profile.id(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RecipeCollection> setRecipeCollectionPicture(UUID collectionId, MultipartFile file) {
        UserProfileRecord profile = currentUser.requireProfile();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("the picture could not be read", e);
        }
        return ResponseEntity.ok(RecipeApiMapper.toApiCollection(
                collections.setPicture(profile.householdId(), profile.id(), collectionId, bytes)));
    }

    @Override
    public ResponseEntity<Void> deleteRecipeCollectionPicture(UUID collectionId) {
        collections.removePicture(currentUser.requireProfile().householdId(), collectionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> getRecipeCollectionPicture(UUID collectionId, UUID pictureId) {
        PictureContent content =
                collections.picture(currentUser.requireProfile().householdId(), collectionId, pictureId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.format().mediaType()))
                .contentLength(content.bytes().length)
                .cacheControl(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(content.bytes()));
    }
}
