package io.github.noahhhx.mimos.recipes.collection;

import java.util.List;
import java.util.UUID;

/** A collection as submitted for creation or replacement, before validation. */
public record RecipeCollectionDraft(String name, List<String> tags, List<UUID> recipeIds) {}
