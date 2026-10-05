import type { LibraryRecipe, PlannedSlot, PluginSuggestionCard } from "@mimos/plugin-sdk";

import { COUNTRIES, type Country } from "./countries.ts";

/**
 * Country of the Week's suggestion logic — pure functions of the context
 * Mimos sends (no clock, no randomness: the week itself picks the
 * country, so a week always suggests the same one).
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

/** Days since the Unix epoch for an ISO week-start date — the deterministic picker's index. */
export function dayNumber(weekStartDate: string): number {
  return Math.floor(Date.parse(`${weekStartDate}T00:00:00Z`) / 86_400_000);
}

/**
 * The country this week leans into: deterministic in the week, among the
 * countries whose cuisine the given catalog can actually cook. `null`
 * when nothing matches.
 */
export function pickCountry(weekStartDate: string, recipes: LibraryRecipe[]): Country | null {
  const candidates = COUNTRIES.filter((country) => matchesFor(country, recipes).length > 0);
  if (candidates.length === 0) {
    return null;
  }
  return candidates[Math.abs(dayNumber(weekStartDate)) % candidates.length] ?? null;
}

/**
 * Builds the card: matched recipes into the week's still-unplanned dinner
 * slots, Monday forward. All dinners already planned (or nothing
 * matching) means no card — the plugin never re-plans a taken slot.
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
    icon: country.icon,
    entries,
  };
}

/** The full suggestion for a context: a card, or nothing to suggest. */
export function suggest(context: {
  weekStartDate: string;
  plannedSlots: PlannedSlot[];
  libraryRecipes: LibraryRecipe[];
}): PluginSuggestionCard[] {
  const country = pickCountry(context.weekStartDate, context.libraryRecipes);
  if (country === null) {
    return [];
  }
  const card = buildCard(country, context.libraryRecipes, context.plannedSlots, context.weekStartDate);
  return card === null ? [] : [card];
}
