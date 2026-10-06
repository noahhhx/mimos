import type { CatalogIngredient, IngredientLineStatus, RecipeSummary } from "@mimos/api-client";

import {
  containsAtWordStart,
  describeEntry,
  lineNameOf,
  mentions,
  searchByName,
  suggestCatalogSlug,
  UNITS_BY_BASIS,
} from "./catalog-match.ts";
import { formatServings } from "./format.ts";
import { SERVINGS, type IngredientRow } from "./recipe-input.ts";

/**
 * What an ingredient row counts as for calculated nutrition, and how
 * typing and picking change it: a catalog entry (ADR-0015, ADR-0016) or
 * one of the user's own recipes, in servings (ADR-0018). A recipe is only
 * ever linked by picking it, never by its name.
 */
export type LineLink = { kind: "entry"; entry: CatalogIngredient } | { kind: "recipe"; recipe: RecipeSummary };

/** The row's link, among what the form has loaded. */
export function linkOf(
  row: IngredientRow,
  bySlug: Map<string, CatalogIngredient>,
  byRecipeId: Map<string, RecipeSummary>,
): LineLink | undefined {
  if (row.recipeId) {
    const recipe = byRecipeId.get(row.recipeId);
    return recipe && { kind: "recipe", recipe };
  }
  const entry = row.catalogSlug ? bySlug.get(row.catalogSlug) : undefined;
  return entry && { kind: "entry", entry };
}

/** The user's recipes whose title has a word that starts with what was typed, best first. */
export function searchRecipes(query: string, recipes: RecipeSummary[], limit = 4): RecipeSummary[] {
  return searchByName(query, recipes, (recipe) => recipe.title, limit);
}

/** A recipe as a search option's second line, like "Your recipe · 278 kcal per serving". */
export function describeRecipe(recipe: RecipeSummary): string {
  const { calories, proteinG, carbsG, fatG } = recipe.nutrition;
  const known = calories != null && proteinG != null && carbsG != null && fatG != null;
  return `Your recipe · ${known ? `${Math.round(calories)} kcal per serving` : "nutrition unknown"}`;
}

/** What a linked row counts as, in words for the form. */
export function describeLink(link: LineLink): string {
  return link.kind === "entry"
    ? `Matched to ${link.entry.name} (${describeEntry(link.entry)}).`
    : `Uses your recipe ${link.recipe.title} (makes ${formatServings(link.recipe.servings)}).`;
}

/** Why a linked row adds nothing, in words for the form; nothing for a row that counted or is not linked. */
export function lineHint(status: IngredientLineStatus | undefined, link: LineLink | undefined): string | undefined {
  if (!link) {
    return undefined;
  }
  switch (status) {
    case "UNMEASURED":
      return "No amount, so it adds nothing.";
    case "UNIT_NOT_SUPPORTED":
      return link.kind === "entry"
        ? `Not counted. ${link.entry.name} counts in ${UNITS_BY_BASIS[link.entry.basis]}.`
        : "Not counted. A recipe counts in servings.";
    case "NUTRITION_UNKNOWN":
      return link.kind === "recipe" ? `Not counted. ${link.recipe.title}'s nutrition isn't known yet.` : undefined;
    default:
      return undefined;
  }
}

/** Links a row to a catalog entry, keeping what was typed when it already names the entry. */
export function pickEntry(row: IngredientRow, entry: CatalogIngredient): IngredientRow {
  return { ...unlinked(row), name: mentions(row.name, entry) ? row.name : lineNameOf(entry), catalogSlug: entry.slug };
}

/**
 * Links a row to one of the user's recipes, measured in servings of it,
 * keeping what was typed when it already names the recipe.
 */
export function pickRecipe(row: IngredientRow, recipe: RecipeSummary): IngredientRow {
  return {
    ...unlinked(row),
    name: containsAtWordStart(row.name, recipe.title) ? row.name : recipe.title.toLowerCase(),
    unit: SERVINGS,
    recipeId: recipe.id,
  };
}

/** A row its author chose not to count, so it is not linked again by name. */
export function dontCount(row: IngredientRow): IngredientRow {
  return { ...unlinked(row), catalogSlug: "" };
}

/**
 * A row after its name is typed. It keeps its link while the name still
 * names what it links ("garlic clove" to "garlic cloves"), and a recipe
 * link while the recipe is still loading. Otherwise it links the catalog
 * entry its name names, unless its author chose not to count it.
 */
export function retype(
  row: IngredientRow,
  name: string,
  catalog: CatalogIngredient[],
  bySlug: Map<string, CatalogIngredient>,
  byRecipeId: Map<string, RecipeSummary>,
): IngredientRow {
  if (row.catalogSlug === "") {
    return { ...row, name };
  }
  if (row.recipeId) {
    const recipe = byRecipeId.get(row.recipeId);
    if (!recipe || containsAtWordStart(name, recipe.title)) {
      return { ...row, name };
    }
  }
  const entry = row.catalogSlug ? bySlug.get(row.catalogSlug) : undefined;
  if (entry && mentions(name, entry)) {
    return { ...row, name };
  }
  const slug = suggestCatalogSlug(name, catalog);
  return { ...unlinked(row), name, ...(slug ? { catalogSlug: slug } : {}) };
}

function unlinked(row: IngredientRow): IngredientRow {
  const copy = { ...row };
  delete copy.catalogSlug;
  delete copy.recipeId;
  return copy;
}
