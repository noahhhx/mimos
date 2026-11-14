/**
 * Cooking across linked recipes (ADR-0018): which ingredients and steps of
 * each recipe are ticked off, kept for the browser tab so following an
 * ingredient to the recipe it uses and coming back keeps them, and the
 * trail of recipes followed to get here, carried in the URL as `?via=`.
 */

/** What the tab remembers about one recipe while cooking it. */
export type CookState = { title: string; ingredients: number[]; steps: number[] };

/** The part of `Storage` this needs, so tests can pass a stand-in. */
export type CookStorage = Pick<Storage, "getItem" | "setItem">;

const KEY_PREFIX = "mimos.cook.";
const ID = /^[0-9a-f-]{36}$/i;

/** The recipe's remembered state, or none when it was never ticked or the stored value is unreadable. */
export function readCookState(storage: CookStorage, recipeId: string): CookState | undefined {
  const raw = storage.getItem(KEY_PREFIX + recipeId);
  if (!raw) {
    return undefined;
  }
  try {
    const value: unknown = JSON.parse(raw);
    if (typeof value !== "object" || value === null) {
      return undefined;
    }
    const { title, ingredients, steps } = value as Record<string, unknown>;
    return {
      title: typeof title === "string" ? title : "",
      ingredients: indices(ingredients),
      steps: indices(steps),
    };
  } catch {
    return undefined;
  }
}

export function writeCookState(storage: CookStorage, recipeId: string, state: CookState): void {
  storage.setItem(KEY_PREFIX + recipeId, JSON.stringify(state));
}

/**
 * The recipes followed to reach `currentId`, first one first, from the
 * `via` query value. Anything that is not a recipe id is dropped, and the
 * trail is cut before `currentId` or a repeat, so it never loops.
 */
export function parseTrail(via: string | null, currentId: string): string[] {
  const trail: string[] = [];
  for (const id of (via ?? "").split(",")) {
    if (id === currentId || trail.includes(id)) {
      break;
    }
    if (ID.test(id)) {
      trail.push(id);
    }
  }
  return trail;
}

/** Where a recipe in the trail, or the one being followed to, is opened: with the trail that leads to it. */
export function recipeHref(recipeId: string, via: string[]): string {
  return via.length > 0 ? `/app/recipes/${recipeId}?via=${via.join(",")}` : `/app/recipes/${recipeId}`;
}

function indices(value: unknown): number[] {
  return Array.isArray(value) ? value.filter((n): n is number => Number.isInteger(n) && n >= 0) : [];
}
