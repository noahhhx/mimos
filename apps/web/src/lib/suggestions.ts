import { addMealPlanEntry, type PlanSuggestionEntry } from "@mimos/api-client";

import { apiClient } from "./api";

/**
 * Applies plugin-suggested meals to a week through the plan-entry endpoint,
 * the same one the plan page's picker uses: plugins have no write path of
 * their own (ADR-0006). Returns how many were added.
 */
export async function applySuggestionEntries(weekStart: string, entries: PlanSuggestionEntry[]): Promise<number> {
  let added = 0;
  for (const entry of entries) {
    const result = await addMealPlanEntry({
      client: apiClient,
      path: { startDate: weekStart },
      body: {
        date: entry.date,
        mealType: entry.mealType,
        recipeId: entry.recipeId,
        servings: entry.servings,
      },
    });
    if (!result.error) {
      added++;
    }
  }
  return added;
}
