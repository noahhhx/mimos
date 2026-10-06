import { useId, useState } from "react";

import type { CatalogIngredient, RecipeSummary } from "@mimos/api-client";

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
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

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
  const showing = open && options.length > 0;

  const choose = (index: number) => {
    const option = options[index];
    if (!option) {
      return;
    }
    setOpen(false);
    if (option.kind === "entry") {
      onPick(option.entry);
    } else if (option.kind === "recipe") {
      onPickRecipe(option.recipe);
    } else {
      onAdd(typed);
    }
  };

  return (
    <div className="ingredient-picker">
      <label>
        Ingredient
        <input
          role="combobox"
          aria-label={label}
          aria-expanded={showing}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showing ? `${listId}-${active}` : undefined}
          value={name}
          maxLength={200}
          placeholder="Search ingredients"
          autoComplete="off"
          onChange={(e) => {
            onType(e.target.value);
            setActive(0);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown" || e.key === "ArrowUp") {
              e.preventDefault();
              setOpen(true);
              const step = e.key === "ArrowDown" ? 1 : -1;
              setActive((current) => (current + step + options.length) % Math.max(options.length, 1));
            } else if (e.key === "Enter" && showing) {
              e.preventDefault();
              choose(active);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
        />
      </label>
      {showing && (
        <ul className="picker-options" role="listbox" id={listId}>
          {options.map((option, index) => (
            <li
              key={keyOf(option)}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? "active" : undefined}
              // Choosing on mouse down, before the input's blur closes the list.
              onMouseDown={(e) => {
                e.preventDefault();
                choose(index);
              }}
              onMouseEnter={() => setActive(index)}
            >
              {option.kind === "entry" ? (
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
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
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
