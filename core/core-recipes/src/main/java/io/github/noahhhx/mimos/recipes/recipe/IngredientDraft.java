package io.github.noahhhx.mimos.recipes.recipe;

/** A personal ingredient as submitted for creation or replacement, before validation (ADR-0016). */
public record IngredientDraft(String name, NutritionBasis basis, Nutrition nutrition) {}
