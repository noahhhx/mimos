import type { LibraryRecipe, PlannedSlot, PluginSuggestionCard } from "@mimos/plugin-sdk";

import { type Country, flag } from "./countries.ts";

/**
 * Recipe suggestions for the country chosen for a week: pure functions of
 * that country and the context Mimos sends.
 */

/** The library recipes that fit a country's cuisine (tags or title). */
export function matchesFor(country: Country, recipes: LibraryRecipe[]): LibraryRecipe[] {
  const tags = new Set(country.tags);
  const words = country.titleWords;
  return recipes.filter((recipe) => {
    if (recipe.tags.some((tag) => tags.has(tag.toLowerCase()))) {
      return true;
    }
    const title = recipe.title.toLowerCase();
    return words.some((word) => title.includes(word));
  });
}

/**
 * Builds the card: matched recipes into the week's still-unplanned dinner
 * slots, Monday forward. All dinners already planned, or nothing
 * matching, means no card: the plugin never re-plans a taken slot.
 */
export function buildCard(
  country: Country,
  recipes: LibraryRecipe[],
  plannedSlots: PlannedSlot[],
  weekStartDate: string,
): PluginSuggestionCard | null {
  const matched = matchesFor(country, recipes);
  if (matched.length === 0) {
    return null;
  }
  const taken = new Set(plannedSlots.map((slot) => `${slot.date}:${slot.mealType}`));
  const dinnerDates: string[] = [];
  const start = Date.parse(`${weekStartDate}T00:00:00Z`);
  for (let i = 0; i < 7; i++) {
    const date = new Date(start + i * 86_400_000).toISOString().slice(0, 10);
    if (!taken.has(`${date}:DINNER`)) {
      dinnerDates.push(date);
    }
  }
  const entries = matched
    .slice(0, dinnerDates.length)
    .map((recipe, index) => ({
      date: dinnerDates[index] as string,
      mealType: "DINNER" as const,
      recipeId: recipe.id,
      servings: 2,
    }));
  if (entries.length === 0) {
    return null;
  }
  return {
    title: `${country.name} week`,
    blurb: `Lean into ${country.cuisine} cooking with ${entries.length} dinner${entries.length === 1 ? "" : "s"} from the library this week.`,
    icon: flag(country),
    entries,
  };
}

/** The cards for a week: none until a country is chosen for it. */
export function suggest(
  country: Country | null,
  context: { weekStartDate: string; plannedSlots: PlannedSlot[]; libraryRecipes: LibraryRecipe[] },
): PluginSuggestionCard[] {
  if (country === null) {
    return [];
  }
  const card = buildCard(country, context.libraryRecipes, context.plannedSlots, context.weekStartDate);
  return card === null ? [] : [card];
}
