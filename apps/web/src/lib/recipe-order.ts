import type { RecipeSummary } from "@mimos/api-client";

export const RECIPE_SORTS = {
  title: { label: "Title, A to Z" },
  newest: { label: "Newest first" },
  oldest: { label: "Oldest first" },
  edited: { label: "Recently edited" },
} as const;

export type RecipeSort = keyof typeof RECIPE_SORTS;

export const DEFAULT_RECIPE_SORT: RecipeSort = "title";

const titleCollator = new Intl.Collator(undefined, { sensitivity: "base" });

function titleCompare(a: RecipeSummary, b: RecipeSummary): number {
  const byTitle = titleCollator.compare(a.title, b.title);
  return byTitle === 0 ? a.id.localeCompare(b.id) : byTitle;
}

function dateCompare(a: RecipeSummary, b: RecipeSummary, newestFirst: boolean): number {
  const byDate = newestFirst ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt);
  return byDate === 0 ? titleCompare(a, b) : byDate;
}

function editedCompare(a: RecipeSummary, b: RecipeSummary): number {
  const byEdited = b.updatedAt.localeCompare(a.updatedAt);
  return byEdited === 0 ? titleCompare(a, b) : byEdited;
}

const comparators: Record<RecipeSort, (a: RecipeSummary, b: RecipeSummary) => number> = {
  title: titleCompare,
  newest: (a, b) => dateCompare(a, b, true),
  oldest: (a, b) => dateCompare(a, b, false),
  edited: editedCompare,
};

export function parseRecipeSort(value: string | null | undefined): RecipeSort {
  if (value && value in RECIPE_SORTS) {
    return value as RecipeSort;
  }
  return DEFAULT_RECIPE_SORT;
}

export function recipeTags(recipes: readonly RecipeSummary[]): string[] {
  const seen = new Set<string>();
  for (const recipe of recipes) {
    for (const tag of recipe.tags) {
      seen.add(tag);
    }
  }
  return [...seen].sort((a, b) => titleCollator.compare(a, b));
}

export function orderRecipes(
  recipes: readonly RecipeSummary[],
  sort: RecipeSort,
  selectedTags: ReadonlySet<string>,
): RecipeSummary[] {
  const filtered = selectedTags.size === 0
    ? recipes.slice()
    : recipes.filter((recipe) => {
        for (const tag of selectedTags) {
          if (!recipe.tags.includes(tag)) {
            return false;
          }
        }
        return true;
      });
  return filtered.sort(comparators[sort]);
}
