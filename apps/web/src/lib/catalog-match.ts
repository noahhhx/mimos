import type { CatalogIngredient, IngredientLineStatus, NutritionBasis } from "@mimos/api-client";

/**
 * Helpers for linking recipe lines to the ingredient catalog (ADR-0015):
 * guessing the entry a line names, and saying why a line did not count.
 */

const UNITS_BY_BASIS: Record<NutritionBasis, string> = {
  PER_100_G: "g or kg",
  PER_100_ML: "ml, l, tsp or tbsp",
  PER_PIECE: "pieces, with no unit",
};

/**
 * The catalog entry a line's name most likely means: the one with the
 * longest name or slug found in it at a word start ("garlic cloves,
 * minced" is a garlic clove; "parsley, chopped" is fresh parsley by its
 * slug; "olive oil" is olive oil, not oil).
 */
export function suggestCatalogSlug(name: string, catalog: CatalogIngredient[]): string | undefined {
  const text = normalize(name);
  let best: { slug: string; length: number } | undefined;
  for (const entry of catalog) {
    for (const phrase of [normalize(entry.name.replace(/\(.*\)|,.*$/g, "")), normalize(entry.slug.replaceAll("-", " "))]) {
      if (phrase !== "" && phrase.length > (best?.length ?? 0) && startsAWord(text, phrase)) {
        best = { slug: entry.slug, length: phrase.length };
      }
    }
  }
  return best?.slug;
}

/** Why a linked line adds nothing, in words for the form; nothing for a line that counted or is not linked. */
export function lineHint(status: IngredientLineStatus | undefined, entry: CatalogIngredient | undefined): string | undefined {
  if (!entry) {
    return undefined;
  }
  switch (status) {
    case "UNMEASURED":
      return "No amount, so it adds nothing.";
    case "UNIT_NOT_SUPPORTED":
      return `Not counted. ${entry.name} counts in ${UNITS_BY_BASIS[entry.basis]}.`;
    default:
      return undefined;
  }
}

function normalize(value: string): string {
  return value.toLowerCase().replace(/\s+/g, " ").trim();
}

function startsAWord(text: string, phrase: string): boolean {
  for (let at = text.indexOf(phrase); at !== -1; at = text.indexOf(phrase, at + 1)) {
    if (at === 0 || !/[a-z]/.test(text[at - 1])) {
      return true;
    }
  }
  return false;
}
