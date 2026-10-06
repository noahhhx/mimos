"use client";

import Link from "next/link";
import { useState } from "react";

import type { RecipeDetail } from "@mimos/api-client";

import { PerServing } from "@/components/per-serving";
import { Quantity } from "@/components/quantity";
import { SplitPage } from "@/components/split-page";
import { hasNutrition } from "@/lib/format";

/**
 * A recipe as you cook it, for library and personal recipes alike. The
 * rail holds what you gather (tickable ingredients, nutrition per serving);
 * the main column holds the method, whose steps are ticked off by tapping
 * them. Ticks are local state only — the flour-on-your-hands view.
 * An ingredient that is one of your recipes links to it (ADR-0018).
 * `children` go under the method.
 */
export function RecipeView({ recipe, children }: { recipe: RecipeDetail; children?: React.ReactNode }) {
  const [doneIngredients, setDoneIngredients] = useState<Set<number>>(new Set());
  const [doneSteps, setDoneSteps] = useState<Set<number>>(new Set());

  const toggle = (set: Set<number>, index: number, apply: (next: Set<number>) => void) => {
    const next = new Set(set);
    if (next.has(index)) {
      next.delete(index);
    } else {
      next.add(index);
    }
    apply(next);
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
            <li key={index}>
              <label className={doneIngredients.has(index) ? "done" : undefined}>
                <input
                  type="checkbox"
                  checked={doneIngredients.has(index)}
                  onChange={() => toggle(doneIngredients, index, setDoneIngredients)}
                />{" "}
                <Quantity quantity={ingredient.quantity} unit={ingredient.unit} />{" "}
                {ingredient.recipeId ? (
                  <Link href={`/app/recipes/${ingredient.recipeId}`}>{ingredient.name}</Link>
                ) : (
                  ingredient.name
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
        <p className="lede">{recipe.description}</p>
        <section>
          <h2>Method</h2>
          <ol className="steps cook">
            {recipe.steps.map((step, index) => (
              <li key={index} className={doneSteps.has(index) ? "done" : undefined}>
                <label>
                  <input
                    type="checkbox"
                    className="visually-hidden"
                    checked={doneSteps.has(index)}
                    onChange={() => toggle(doneSteps, index, setDoneSteps)}
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
