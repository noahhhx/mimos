package io.github.noahhhx.mimos.recipes.recipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingredient use cases (ADR-0016): the catalog a user sees (shared entries
 * and their own) and CRUD on their own. Shared entries are read-only
 * ({@link ReadOnlyIngredientException}, 403); another user's are
 * indistinguishable from nonexistent (404).
 */
@Service
public class IngredientService {

    static final int MAX_NAME_LENGTH = 100;

    private final IngredientCatalog catalog;

    public IngredientService(IngredientCatalog catalog) {
        this.catalog = catalog;
    }

    /** The shared entries and the viewer's own, by name. */
    public List<CatalogIngredient> findVisible(UUID ownerId) {
        return catalog.findVisibleTo(ownerId);
    }

    /** The owner's own entries (for the account export). */
    public List<CatalogIngredient> findOwned(UUID ownerId) {
        return catalog.findOwnedBy(ownerId);
    }

    /** Whether the owner has any ingredients of their own. */
    public boolean hasOwned(UUID ownerId) {
        return catalog.existsOwnedBy(ownerId);
    }

    /** Gives all of one owner's ingredients to another (ADR-0019); recipe lines that link them stay linked. */
    @Transactional
    public void moveAll(UUID fromOwnerId, UUID toOwnerId) {
        catalog.moveOwned(fromOwnerId, toOwnerId);
    }

    /** The entries with these slugs that the viewer may link; others are omitted. */
    public Map<String, CatalogIngredient> findVisibleBySlugs(UUID ownerId, Collection<String> slugs) {
        Map<String, CatalogIngredient> found = new HashMap<>(catalog.findBySlugs(slugs));
        found.values().removeIf(entry -> !entry.isVisibleTo(ownerId));
        return found;
    }

    /** Creates a personal ingredient with a new slug made from its name. */
    @Transactional
    public CatalogIngredient create(UUID ownerId, IngredientDraft draft) {
        CatalogIngredient created = toEntry(slugFor(draft.name()), ownerId, draft);
        catalog.insert(created);
        return created;
    }

    /** Replaces a personal ingredient's name and nutrition; its slug, and so every link to it, stays. */
    @Transactional
    public CatalogIngredient replace(UUID ownerId, String slug, IngredientDraft draft) {
        requireOwned(ownerId, slug);
        CatalogIngredient replaced = toEntry(slug, ownerId, draft);
        catalog.update(replaced);
        return replaced;
    }

    /** Deletes a personal ingredient; recipe lines that linked it stay, unlinked. */
    @Transactional
    public void delete(UUID ownerId, String slug) {
        requireOwned(ownerId, slug);
        catalog.delete(slug);
    }

    private void requireOwned(UUID ownerId, String slug) {
        CatalogIngredient existing = catalog.findBySlugs(List.of(slug)).get(slug);
        if (existing == null || !existing.isVisibleTo(ownerId)) {
            throw new NoSuchElementException("ingredient not found: " + slug);
        }
        if (existing.isShared()) {
            throw new ReadOnlyIngredientException("shared ingredients are read-only");
        }
    }

    private static CatalogIngredient toEntry(String slug, UUID ownerId, IngredientDraft draft) {
        String name = draft.name() == null ? "" : draft.name().strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "an ingredient needs a name of at most " + MAX_NAME_LENGTH + " characters");
        }
        Nutrition nutrition = draft.nutrition();
        return new CatalogIngredient(
                slug,
                ownerId,
                name,
                draft.basis(),
                required("calories", nutrition.calories()),
                required("proteinG", nutrition.proteinG()),
                required("carbsG", nutrition.carbsG()),
                required("fatG", nutrition.fatG()));
    }

    private static double required(String field, @Nullable Double value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required for an ingredient; use 0 when there is none");
        }
        return value;
    }

    /** A readable, unique slug: the name's words, then a random suffix ("dragon-fruit-k3f9q2"). */
    private static String slugFor(String name) {
        String words =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (words.length() > 40) {
            words = words.substring(0, 40).replaceAll("-+$", "");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return (words.isEmpty() ? "ingredient" : words) + "-" + suffix;
    }
}
