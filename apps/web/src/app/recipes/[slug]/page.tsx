import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { getPublicRecipe } from "@mimos/api-client";

import { hasNutrition } from "@/lib/format";
import { publicApi } from "@/lib/server-api";

/** Public recipe pages render server-side so recipes are findable (SEO). */
export const revalidate = 600;

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const result = await getPublicRecipe({ client: publicApi, path: { slug } });
  if (result.error || !result.data) {
    return { title: "Recipe not found — Mimos" };
  }
  return {
    title: `${result.data.title} — Mimos`,
    description: result.data.description,
  };
}

export default async function RecipePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const result = await getPublicRecipe({ client: publicApi, path: { slug } });
  const recipe = result.data;
  if (result.error || !recipe) {
    notFound();
  }

  return (
    <article className="recipe">
      <p className="back">
        <Link href="/recipes">← Library</Link>
      </p>
      <header className="page-header">
        <h1>{recipe.title}</h1>
        <p className="lede">{recipe.description}</p>
        <p className="muted">
          Serves {recipe.servings}
          {recipe.prepMinutes != null ? ` · ${recipe.prepMinutes} min prep` : ""}
          {recipe.cookMinutes != null ? ` · ${recipe.cookMinutes} min cook` : ""}
        </p>
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

      {hasNutrition(recipe.nutrition) && (
        <div className="card nutrition">
          <h2>Per serving</h2>
          <dl className="profile">
            <dt>Calories</dt>
            <dd>{recipe.nutrition.calories != null ? `${Math.round(recipe.nutrition.calories)} kcal` : "—"}</dd>
            <dt>Protein</dt>
            <dd>{recipe.nutrition.proteinG != null ? `${Math.round(recipe.nutrition.proteinG)} g` : "—"}</dd>
            <dt>Carbs</dt>
            <dd>{recipe.nutrition.carbsG != null ? `${Math.round(recipe.nutrition.carbsG)} g` : "—"}</dd>
            <dt>Fat</dt>
            <dd>{recipe.nutrition.fatG != null ? `${Math.round(recipe.nutrition.fatG)} g` : "—"}</dd>
          </dl>
        </div>
      )}

      <div className="recipe-columns">
        <section className="card">
          <h2>Ingredients</h2>
          <ul className="ingredients">
            {recipe.ingredients.map((ingredient, index) => (
              <li key={index}>
                <span className="quantity">
                  {ingredient.quantity}
                  {ingredient.unit ? ` ${ingredient.unit}` : ""}
                </span>{" "}
                {ingredient.name}
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>Steps</h2>
          <ol className="steps">
            {recipe.steps.map((step, index) => (
              <li key={index}>{step.instruction}</li>
            ))}
          </ol>
        </section>
      </div>

      <p className="muted">
        Cook this every week? <Link href="/app">Plan it in Mimos</Link> — the shopping list builds itself.
      </p>
    </article>
  );
}
