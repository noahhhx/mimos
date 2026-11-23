package io.github.noahhhx.mimos.recipes.collection;

import io.github.noahhhx.mimos.recipes.recipe.PictureContent;
import io.github.noahhhx.mimos.recipes.recipe.PictureFormat;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

/**
 * Recipe collections (ADR-0036): household data that any member may
 * change, read by one person at a time, because each person picks their
 * own default. Another household's collection is a 404. Validation failures
 * are {@link IllegalArgumentException} (400) and a second collection with a
 * name the household already uses is {@link CollectionNameTakenException}
 * (409).
 */
@Service
@Transactional
public class RecipeCollectionService {

    static final int MAX_NAME_LENGTH = 60;
    static final int MAX_TAGS = 12;
    static final int MAX_TAG_LENGTH = 30;
    static final int MAX_PICKS = 500;

    private final RecipeCollectionRepository repository;
    private final RecipeService recipes;
    private final Clock clock;
    private final IdGenerator ids;

    public RecipeCollectionService(
            RecipeCollectionRepository repository, RecipeService recipes, Clock clock, IdGenerator ids) {
        this.repository = repository;
        this.recipes = recipes;
        this.clock = clock;
        this.ids = ids;
    }

    /** The owner's collections in the order they were made. */
    @Transactional(readOnly = true)
    public List<RecipeCollection> list(UUID ownerId, UUID personId) {
        return view(ownerId, personId, repository.findOwned(ownerId));
    }

    @Transactional(readOnly = true)
    public RecipeCollection get(UUID ownerId, UUID personId, UUID collectionId) {
        return view(ownerId, personId, List.of(requireOwned(ownerId, collectionId)))
                .getFirst();
    }

    public RecipeCollection create(UUID ownerId, UUID personId, RecipeCollectionDraft submitted) {
        return restore(ownerId, personId, submitted, clock.instant());
    }

    /** Creates a collection with the time it was first made, for an account import (ADR-0011). */
    public RecipeCollection restore(UUID ownerId, UUID personId, RecipeCollectionDraft submitted, Instant createdAt) {
        RecipeCollectionDraft draft = validated(ownerId, submitted);
        UUID id = ids.generateId();
        repository.insert(id, ownerId, draft.name(), createdAt);
        repository.replaceContents(id, draft.tags(), draft.recipeIds());
        return get(ownerId, personId, id);
    }

    /** Replaces the name, tags, and picks; the cover and everyone's defaults stay. */
    public RecipeCollection replace(UUID ownerId, UUID personId, UUID collectionId, RecipeCollectionDraft submitted) {
        repository.lock(collectionId);
        requireOwned(ownerId, collectionId);
        RecipeCollectionDraft draft = validated(ownerId, submitted);
        repository.rename(collectionId, draft.name());
        repository.replaceContents(collectionId, draft.tags(), draft.recipeIds());
        return get(ownerId, personId, collectionId);
    }

    /** Deletes a collection; whoever had it as their default has none. Its recipes stay. */
    public void delete(UUID ownerId, UUID collectionId) {
        requireOwned(ownerId, collectionId);
        repository.delete(collectionId);
    }

    /** The collection's member recipes, optionally filtered by a search term. */
    @Transactional(readOnly = true)
    public List<Recipe> members(UUID ownerId, UUID collectionId, @Nullable String query) {
        requireOwned(ownerId, collectionId);
        return recipes.findAmong(repository.memberIds(ownerId, collectionId), query);
    }

    /** Sets or replaces the cover, a picture with the same rules as a recipe picture (ADR-0023). */
    public RecipeCollection setPicture(UUID ownerId, UUID personId, UUID collectionId, byte[] bytes) {
        repository.lock(collectionId);
        requireOwned(ownerId, collectionId);
        PictureFormat format = RecipePictureService.requirePicture(bytes);
        repository.putPicture(collectionId, ids.generateId(), format, bytes);
        return get(ownerId, personId, collectionId);
    }

    /** Removes the cover; removing none is not an error. */
    public void removePicture(UUID ownerId, UUID collectionId) {
        requireOwned(ownerId, collectionId);
        repository.deletePicture(collectionId);
    }

    /** The cover's image while it is the collection's current one; a 404 for anything else. */
    @Transactional(readOnly = true)
    public PictureContent picture(UUID ownerId, UUID collectionId, UUID pictureId) {
        requireOwned(ownerId, collectionId);
        return repository
                .picture(collectionId, pictureId)
                .orElseThrow(() -> new NoSuchElementException("picture not found: " + pictureId));
    }

    /** Makes the collection the one this person opens Recipes on, in place of any other. */
    public void setDefault(UUID ownerId, UUID personId, UUID collectionId) {
        requireOwned(ownerId, collectionId);
        repository.setDefault(personId, collectionId);
    }

    /** Stops this person opening Recipes on the collection; a no-op when it was not their default. */
    public void clearDefault(UUID ownerId, UUID personId, UUID collectionId) {
        requireOwned(ownerId, collectionId);
        repository.clearDefault(personId, collectionId);
    }

    @Transactional(readOnly = true)
    public boolean hasOwned(UUID ownerId) {
        return repository.existsOwnedBy(ownerId);
    }

    /** Deletes every collection the owner has, for a household that is going away. */
    public void deleteAll(UUID ownerId) {
        repository.deleteOwned(ownerId);
    }

    private RecipeCollectionRepository.Row requireOwned(UUID ownerId, UUID collectionId) {
        return repository
                .find(ownerId, collectionId)
                .orElseThrow(() -> new NoSuchElementException("tab not found: " + collectionId));
    }

    private List<RecipeCollection> view(UUID ownerId, UUID personId, List<RecipeCollectionRepository.Row> rows) {
        List<UUID> collectionIds =
                rows.stream().map(RecipeCollectionRepository.Row::id).toList();
        Map<UUID, List<String>> tags = repository.tags(collectionIds);
        Map<UUID, List<UUID>> picks = repository.picks(collectionIds);
        Optional<UUID> chosen = repository.defaultOf(ownerId, personId);
        return rows.stream()
                .map(row -> new RecipeCollection(
                        row.id(),
                        row.ownerId(),
                        row.name(),
                        tags.getOrDefault(row.id(), List.of()),
                        picks.getOrDefault(row.id(), List.of()),
                        row.pictureId(),
                        row.createdAt(),
                        chosen.filter(row.id()::equals).isPresent(),
                        repository.memberIds(ownerId, row.id()).size()))
                .toList();
    }

    /**
     * The draft with its name trimmed, its tags trimmed, lowercased, and
     * distinct, and its picks distinct, each one a recipe the owner may see.
     */
    private RecipeCollectionDraft validated(UUID ownerId, RecipeCollectionDraft draft) {
        String name = draft.name() == null ? "" : draft.name().strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        List<String> tags = draft.tags().stream()
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (tags.size() > MAX_TAGS) {
            throw new IllegalArgumentException("a tab can gather at most " + MAX_TAGS + " tags");
        }
        for (String tag : tags) {
            if (tag.isEmpty()) {
                throw new IllegalArgumentException("tag is required");
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException("tag must be at most " + MAX_TAG_LENGTH + " characters");
            }
        }
        List<UUID> picks = draft.recipeIds().stream().distinct().toList();
        if (picks.size() > MAX_PICKS) {
            throw new IllegalArgumentException("a tab can have at most " + MAX_PICKS + " picked recipes");
        }
        Map<UUID, Recipe> found = recipes.findByIds(picks);
        for (UUID id : picks) {
            Recipe recipe = found.get(id);
            if (recipe == null || !(recipe.isLibrary() || ownerId.equals(recipe.ownerId()))) {
                throw new IllegalArgumentException("you have no recipe with id " + id);
            }
        }
        return new RecipeCollectionDraft(name, tags, picks);
    }
}
