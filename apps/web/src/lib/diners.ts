import type { MealPlanEntry, MealType } from "@mimos/api-client";

/**
 * Who eats a planned meal (ADR-0019), as the plan page needs it: whose
 * meals to show and what a new meal starts with.
 */

/** The plan page's toggle: the caller's meals, or the whole household's. */
export type PlanView = "mine" | "everyone";

/** A stored view, or "mine" when there is none or it is not a view. */
export function parsePlanView(stored: string | null): PlanView {
  return stored === "everyone" ? "everyone" : "mine";
}

/** Whether the caller is one of the meal's diners. */
export function eatsIt(entry: Pick<MealPlanEntry, "diners">): boolean {
  return entry.diners.some((diner) => diner.you);
}

/** The entries a view shows: "mine" keeps the meals the caller eats, shared ones included. */
export function visibleEntries<T extends Pick<MealPlanEntry, "diners">>(entries: T[], view: PlanView): T[] {
  return view === "everyone" ? entries : entries.filter(eatsIt);
}

/**
 * The servings a new meal starts with. A household of one keeps 2; a
 * shared household cooks one serving for each of the meal's default
 * diners: every member at dinner, the caller alone otherwise.
 */
export function defaultServings(mealType: MealType, memberCount: number): number {
  return memberCount <= 1 ? 2 : defaultDinerCount(mealType, memberCount);
}

/** How many people a new meal is for by default: every member at dinner, the caller alone otherwise. */
export function defaultDinerCount(mealType: MealType, memberCount: number): number {
  return memberCount > 1 && mealType === "DINNER" ? memberCount : 1;
}

/** The diners after tapping a member; `null` when that would leave the meal with nobody. */
export function toggleDiner(dinerIds: readonly string[], memberId: string): string[] | null {
  if (!dinerIds.includes(memberId)) {
    return [...dinerIds, memberId];
  }
  return dinerIds.length === 1 ? null : dinerIds.filter((id) => id !== memberId);
}

/**
 * A short label for each member's chip: their initials ("Sam Lee" is
 * "SL"), or, where two members' initials match, the start of their name
 * ("Sam" and "Sara" are "Sam" and "Sar").
 */
export function chipLabels(names: readonly string[]): string[] {
  const initials = names.map(initialsOf);
  return names.map((name, index) => {
    const clashing = names.filter((_, other) => initials[other] === initials[index]);
    if (clashing.length === 1) {
      return initials[index];
    }
    const start = (each: string, length: number) => each.trim().slice(0, length).toLowerCase();
    const two = start(name, 2);
    const twoIsEnough = clashing.filter((other) => start(other, 2) === two).length === 1;
    return capitalized(twoIsEnough ? two : start(name, 3));
  });
}

function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return "?";
  }
  const first = words[0][0];
  const last = words.length > 1 ? words[words.length - 1][0] : "";
  return `${first}${last}`.toUpperCase();
}

function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
