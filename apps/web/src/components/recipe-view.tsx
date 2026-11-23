"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import type { RecipeDetail } from "@mimos/api-client";

import { PerServing } from "@/components/per-serving";
import { Quantity } from "@/components/quantity";
import { RecipePictures } from "@/components/recipe-pictures";
import { SplitPage } from "@/components/split-page";
import { readCookState, recipeHref, writeCookState } from "@/lib/cook-trail";
import { countedName, hasNutrition } from "@/lib/format";

/**
 * A recipe as you cook it, for library and personal recipes alike. The
 * rail holds what you gather (tickable ingredients, nutrition per serving);
 * the main column holds the method, whose steps are ticked off by tapping
 * them: the flour-on-your-hands view. Ticks stay for the browser tab, so
 * leaving the recipe and coming back keeps them. An ingredient that is one
 * of your recipes links to it (ADR-0018), carrying `via`, the recipes
 * followed to reach this one, plus this one.
 * Pictures, when it has any, open the main column (ADR-0023).
 * `children` go under the method.
 */
export function RecipeView({
  recipe,
  via = [],
  children,
}: {
  recipe: RecipeDetail;
  via?: string[];
  children?: React.ReactNode;
}) {
  const [doneIngredients, setDoneIngredients] = useState<Set<number>>(new Set());
  const [doneSteps, setDoneSteps] = useState<Set<number>>(new Set());

  // Read after mount, not during render: the public page is server-rendered.
  useEffect(() => {
    try {
      const saved = readCookState(window.sessionStorage, recipe.id);
      setDoneIngredients(new Set(saved?.ingredients.filter((i) => i < recipe.ingredients.length)));
      setDoneSteps(new Set(saved?.steps.filter((i) => i < recipe.steps.length)));
      writeCookState(window.sessionStorage, recipe.id, {
        title: recipe.title,
        ingredients: saved?.ingredients ?? [],
        steps: saved?.steps ?? [],
      });
    } catch {
      // Storage can be unavailable (private windows, blocked site data); ticks then last as long as the page.
    }
  }, [recipe]);

  const save = (ingredients: Set<number>, steps: Set<number>) => {
    try {
      writeCookState(window.sessionStorage, recipe.id, {
        title: recipe.title,
        ingredients: [...ingredients],
        steps: [...steps],
      });
    } catch {
      // As above: unsaved ticks still show.
    }
  };

  const flip = (set: Set<number>, index: number) => {
    const next = new Set(set);
    if (next.has(index)) {
      next.delete(index);
    } else {
      next.add(index);
    }
    return next;
  };

  const toggleIngredient = (index: number) => {
    const next = flip(doneIngredients, index);
    setDoneIngredients(next);
    save(next, doneSteps);
  };

  const toggleStep = (index: number) => {
    const next = flip(doneSteps, index);
    setDoneSteps(next);
    save(doneIngredients, next);
  };

  const rail = (
    <>
      <p className="muted recipe-meta">
        Serves {recipe.servings}
        {recipe.prepMinutes != null ? ` · ${recipe.prepMinutes} min prep` : ""}
        {recipe.cookMinutes != null ? ` · ${recipe.cookMinutes} min cook` : ""}
      </p>
      <header className="page-header">
        <h1>{recipe.title}</h1>
        {recipe.tags.length > 0 && (
          <p className="tags">
            {recipe.tags.map((tag) => (
              <span key={tag} className="tag">
                {tag}
              </span>
            ))}
          </p>
        )}
      </header>

      <section>
        <h2>Ingredients</h2>
        <ul className="ingredients cook">
          {recipe.ingredients.map((ingredient, index) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: a saved recipe's lines never move while it is shown, and the cook ticks are kept by position.
            <li key={index}>
              <label className={doneIngredients.has(index) ? "done" : undefined}>
                <input type="checkbox" checked={doneIngredients.has(index)} onChange={() => toggleIngredient(index)} />{" "}
                <Quantity quantity={ingredient.quantity} unit={ingredient.unit} />{" "}
                {ingredient.recipeId ? (
                  <Link href={recipeHref(ingredient.recipeId, [...via, recipe.id])}>{ingredient.name}</Link>
                ) : (
                  countedName(ingredient.name, ingredient.quantity, ingredient.unit)
                )}
                {ingredient.note ? `, ${ingredient.note}` : ""}
              </label>
            </li>
          ))}
        </ul>
      </section>

      {hasNutrition(recipe.nutrition) && (
        <section className="nutrition">
          <h2>Per serving</h2>
          <PerServing nutrition={recipe.nutrition} />
          {recipe.nutritionSource === "INGREDIENTS" && <p className="muted">Calculated from the ingredients.</p>}
        </section>
      )}
    </>
  );

  return (
    <article className="recipe">
      <SplitPage rail={rail}>
        <RecipePictures recipe={recipe} />
        <p className="lede">{recipe.description}</p>
        <section>
          <h2>Method</h2>
          <ol className="steps cook">
            {recipe.steps.map((step, index) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: a saved recipe's steps never move while it is shown, and the cook ticks are kept by position.
              <li key={index} className={doneSteps.has(index) ? "done" : undefined}>
                <label>
                  <input
                    type="checkbox"
                    className="visually-hidden"
                    checked={doneSteps.has(index)}
                    onChange={() => toggleStep(index)}
                  />
                  {step.instruction}
                </label>
              </li>
            ))}
          </ol>
        </section>
        {children}
      </SplitPage>
    </article>
  );
}
