package io.github.noahhhx.mimos.recipes.recipe;

import java.util.UUID;

/**
 * One picture of a recipe: what it is, not its bytes, which are in the
 * {@link PictureStore}. A picture never changes once added.
 */
public record RecipePicture(UUID id, PictureFormat format) {}
