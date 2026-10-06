import Link from "next/link";

import type { RecipeSummary } from "@mimos/api-client";

import { formatServings } from "@/lib/format";

/**
 * Recipes as rows: title, description and tags, servings and calories on
 * the right, with who added it when that was someone else in the
 * household. The whole row is the link.
 */
export function RecipeList({
  recipes,
  hrefOf,
}: {
  recipes: RecipeSummary[];
  hrefOf: (recipe: RecipeSummary) => string;
}) {
  return (
    <ul className="recipe-list">
      {recipes.map((recipe) => (
        <li key={recipe.id}>
          <h2>
            <Link href={hrefOf(recipe)}>{recipe.title}</Link>
          </h2>
          <div>
            <p className="muted">{recipe.description}</p>
            {recipe.createdBy && !recipe.createdBy.you && (
              <p className="muted">Added by {recipe.createdBy.displayName}</p>
            )}
            {recipe.tags.length > 0 && (
              <p className="tags">
                {recipe.tags.map((tag) => (
                  <span key={tag} className="tag">
                    {tag}
                  </span>
                ))}
              </p>
            )}
          </div>
          <p className="recipe-list-meta">
            {formatServings(recipe.servings)}
            {recipe.nutrition?.calories != null ? ` · ${Math.round(recipe.nutrition.calories)} kcal` : ""}
          </p>
        </li>
      ))}
    </ul>
  );
}
