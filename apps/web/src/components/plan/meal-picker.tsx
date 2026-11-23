"use client";

import { useEffect, useState } from "react";

import {
  createIngredient,
  listLibraryRecipes,
  listMyRecipes,
  type CatalogIngredient,
  type FoodProduct,
  type MealPlanEntryInput,
  type RecipeSummary,
} from "@mimos/api-client";

import { NewIngredient } from "@/components/new-ingredient";
import { ScanBarcode } from "@/components/scan-barcode";
import { apiClient } from "@/lib/api";
import { searchCatalog } from "@/lib/catalog-match";
import { defaultServings } from "@/lib/diners";
import { type MEAL_TYPES, dayLabel, formatServings, mealLabel } from "@/lib/format";
import { describeIngredient, productName } from "@/lib/log-entry";
import { existingIngredient, newFoodEntry, productIngredient } from "@/lib/planned-item";

export type PlanSlot = { date: string; mealType: (typeof MEAL_TYPES)[number] };
export type PlanItem = Pick<MealPlanEntryInput, "recipeId" | "food" | "servings">;

/**
 * The dialog that plans a meal into one slot: a search over the household's
 * and the library's recipes and the ingredient catalog, and a barcode scan
 * that makes a product one of the household's ingredients first. Its search
 * lasts while it stays open, even when another slot's + retargets it.
 */
export function MealPicker({
  slot,
  householdSize,
  ingredients,
  onIngredientAdded,
  onPlan,
  onError,
  onClose,
}: {
  slot: PlanSlot;
  householdSize: number;
  ingredients: CatalogIngredient[];
  onIngredientAdded: (created: CatalogIngredient) => void;
  onPlan: (item: PlanItem) => Promise<void>;
  onError: (message: string) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<RecipeSummary[] | null>(null);
  // A scanned product that lacks a value, until the person fills it in.
  const [unfinished, setUnfinished] = useState<FoodProduct | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const search = query ? { q: query } : {};
      const [mine, library] = await Promise.all([
        listMyRecipes({ client: apiClient, query: search }),
        listLibraryRecipes({ client: apiClient, query: search }),
      ]);
      if (cancelled) {
        return;
      }
      setResults([...(mine.data ?? []), ...(library.data ?? [])]);
    })();
    return () => {
      cancelled = true;
    };
  }, [query]);

  const foodResults = searchCatalog(query, ingredients);

  const planRecipe = (recipeId: string) =>
    onPlan({ recipeId, servings: defaultServings(slot.mealType, householdSize) });

  const planFood = (food: CatalogIngredient, servingSize?: number) =>
    onPlan(newFoodEntry({ slug: food.slug, basis: food.basis, servingSize }, slot.mealType, householdSize));

  // A product with all four values becomes one of the household's ingredients, once, and is planned.
  const planProduct = async (product: FoodProduct) => {
    const input = productIngredient(product);
    if (!input) {
      setUnfinished(product);
      return;
    }
    let food = existingIngredient(input, ingredients);
    if (!food) {
      const created = await createIngredient({ client: apiClient, body: input });
      if (!created.data) {
        onError("Could not add that product.");
        return;
      }
      food = created.data;
      onIngredientAdded(created.data);
    }
    await planFood(food, product.servingSize);
  };

  return (
    <div className="picker card" role="dialog" aria-label="Pick a meal">
      <div className="toolbar">
        <h2>
          {mealLabel(slot.mealType)} · {dayLabel(slot.date)}
        </h2>
        <button type="button" className="button secondary" onClick={onClose}>
          Close
        </button>
      </div>
      <div className="picker-search">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search recipes and ingredients…"
          aria-label="Search recipes and ingredients"
          // biome-ignore lint/a11y/noAutofocus: the picker opens on the person's click, and its search is where they type next.
          autoFocus
        />
        <ScanBarcode onFound={(product) => void planProduct(product)} />
      </div>
      {unfinished ? (
        <NewIngredient
          initialName={productName(unfinished)}
          initialBasis={unfinished.basis}
          initialNutrition={unfinished.nutrition}
          onCreated={(created) => {
            onIngredientAdded(created);
            void planFood(created, created.basis === unfinished.basis ? unfinished.servingSize : undefined);
          }}
          onCancel={() => setUnfinished(null)}
        />
      ) : results === null ? (
        <p className="muted">Searching…</p>
      ) : results.length === 0 && foodResults.length === 0 ? (
        <p className="muted">Nothing matches.</p>
      ) : (
        <ul className="picker-results">
          {results.map((recipe) => (
            <li key={recipe.id}>
              <button type="button" className="button secondary" onClick={() => void planRecipe(recipe.id)}>
                {recipe.title}
                <span className="muted">
                  {" "}
                  · {formatServings(recipe.servings)}
                  {recipe.isLibrary ? " · library" : ""}
                </span>
              </button>
            </li>
          ))}
          {foodResults.map((food) => (
            <li key={food.slug}>
              <button type="button" className="button secondary" onClick={() => void planFood(food)}>
                {food.name}
                <span className="muted"> · {describeIngredient(food)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
