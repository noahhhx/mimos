package io.github.noahhhx.mimos.recipes.collection;

import java.util.List;
import java.util.UUID;

public record RecipeCollectionDraft(String name, List<String> tags, List<UUID> recipeIds) {}
