import type { CatalogIngredient, RecipeSummary } from "@mimos/api-client";

import { Combobox } from "@/components/combobox";
import { describeEntry, searchCatalog } from "@/lib/catalog-match";
import { describeRecipe, searchRecipes } from "@/lib/ingredient-links";

type Option = { kind: "entry"; entry: CatalogIngredient } | { kind: "recipe"; recipe: RecipeSummary } | { kind: "add" };

/**
 * An ingredient line's name, typed into a search over the ingredients the
 * user can count (ADR-0016) and the user's own recipes (ADR-0018). Picking
 * one links the line; when nothing fits, the last option adds what was
 * typed as a new ingredient of the user's own.
 */
export function IngredientPicker({
  label,
  name,
  catalog,
  recipes,
  onType,
  onPick,
  onPickRecipe,
  onAdd,
}: {
  label: string;
  name: string;
  catalog: CatalogIngredient[];
  recipes: RecipeSummary[];
  onType: (name: string) => void;
  onPick: (entry: CatalogIngredient) => void;
  onPickRecipe: (recipe: RecipeSummary) => void;
  onAdd: (name: string) => void;
}) {
  const matches = searchCatalog(name, catalog);
  const recipeMatches = searchRecipes(name, recipes);
  const typed = name.trim();
  const exact =
    matches.some((entry) => entry.name.toLowerCase() === typed.toLowerCase()) ||
    recipeMatches.some((recipe) => recipe.title.toLowerCase() === typed.toLowerCase());
  const options: Option[] = [
    // The user's own recipe is the likelier meaning: "pesto" is their pesto before the shared one.
    ...recipeMatches.map((recipe) => ({ kind: "recipe" as const, recipe })),
    ...matches.map((entry) => ({ kind: "entry" as const, entry })),
    ...(typed !== "" && !exact ? [{ kind: "add" as const }] : []),
  ];

  return (
    <Combobox
      label="Ingredient"
      ariaLabel={label}
      value={name}
      placeholder="Search ingredients"
      className="ingredient-picker"
      options={options}
      keyOf={keyOf}
      onType={onType}
      onChoose={(option) => {
        if (option.kind === "entry") {
          onPick(option.entry);
        } else if (option.kind === "recipe") {
          onPickRecipe(option.recipe);
        } else {
          onAdd(typed);
        }
      }}
      renderOption={(option) =>
        option.kind === "entry" ? (
          <>
            <span>{option.entry.name}</span>
            <span className="muted">
              {option.entry.isShared ? "" : "Yours · "}
              {describeEntry(option.entry)}
            </span>
          </>
        ) : option.kind === "recipe" ? (
          <>
            <span>{option.recipe.title}</span>
            <span className="muted">{describeRecipe(option.recipe)}</span>
          </>
        ) : (
          <span>+ Add &ldquo;{typed}&rdquo; as a new ingredient</span>
        )
      }
    />
  );
}

function keyOf(option: Option): string {
  switch (option.kind) {
    case "entry":
      return `entry:${option.entry.slug}`;
    case "recipe":
      return `recipe:${option.recipe.id}`;
    case "add":
      return "add";
  }
}
