import type { CatalogIngredient, IngredientLineStatus, NutritionBasis } from "@mimos/api-client";

/**
 * Helpers for linking recipe lines to the ingredient catalog (ADR-0015,
 * ADR-0016): searching it as the author types, keeping a link while the
 * line's name still names its entry, and describing an entry or a line
 * that does not count.
 */

const UNITS_BY_BASIS: Record<NutritionBasis, string> = {
  PER_100_G: "g or kg",
  PER_100_ML: "ml, l, tsp or tbsp",
  PER_PIECE: "pieces, with no unit",
};

const PER_BASIS: Record<NutritionBasis, string> = {
  PER_100_G: "per 100 g",
  PER_100_ML: "per 100 ml",
  PER_PIECE: "each",
};

/**
 * Entries with a word in their name that starts with what the author
 * typed, best first: names that start with it, then the rest.
 */
export function searchCatalog(query: string, catalog: CatalogIngredient[], limit = 8): CatalogIngredient[] {
  const text = normalize(query);
  if (text === "") {
    return [];
  }
  const rank = (entry: CatalogIngredient) => {
    const name = normalize(entry.name);
    if (name.startsWith(text)) {
      return 0;
    }
    return startsAWord(name, text) ? 1 : -1;
  };
  return catalog
    .map((entry) => ({ entry, rank: rank(entry) }))
    .filter(({ rank }) => rank >= 0)
    .sort((a, b) => a.rank - b.rank || a.entry.name.localeCompare(b.entry.name))
    .slice(0, limit)
    .map(({ entry }) => entry);
}

/**
 * The catalog entry a line's name most likely means: the one with the
 * longest name or slug found in it at a word start ("garlic cloves" is a
 * garlic clove; "parsley" is fresh parsley by its slug; "olive oil" is
 * olive oil, not oil).
 */
export function suggestCatalogSlug(name: string, catalog: CatalogIngredient[]): string | undefined {
  const text = normalize(name);
  let best: { slug: string; length: number } | undefined;
  for (const entry of catalog) {
    for (const phrase of phrasesOf(entry)) {
      if (phrase.length > (best?.length ?? 0) && startsAWord(text, phrase)) {
        best = { slug: entry.slug, length: phrase.length };
      }
    }
  }
  return best?.slug;
}

/** Whether a line's name still names the entry, so editing "garlic clove" to "garlic cloves" keeps its link. */
export function mentions(name: string, entry: CatalogIngredient): boolean {
  const text = normalize(name);
  return phrasesOf(entry).some((phrase) => startsAWord(text, phrase));
}

/** The name a line takes when its author picks an entry: "Red lentils, dry" is written "red lentils". */
export function lineNameOf(entry: CatalogIngredient): string {
  return entry.name.replace(/\(.*\)|,.*$/g, "").trim().toLowerCase();
}

/** An entry's calories in words, like "813 kcal per 100 ml" or "4 kcal each". */
export function describeEntry(entry: CatalogIngredient): string {
  return `${Math.round(entry.nutrition.calories ?? 0)} kcal ${PER_BASIS[entry.basis]}`;
}

/** The basis a new ingredient most likely has, from the unit its line is measured in. */
export function basisForUnit(unit: string): NutritionBasis {
  const normalized = normalize(unit);
  if (normalized === "") {
    return "PER_PIECE";
  }
  return ["ml", "l", "tsp", "tbsp"].includes(normalized) ? "PER_100_ML" : "PER_100_G";
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

function phrasesOf(entry: CatalogIngredient): string[] {
  return [lineNameOf(entry), normalize(entry.slug.replaceAll("-", " "))].filter((phrase) => phrase !== "");
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
