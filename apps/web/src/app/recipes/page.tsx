import Link from "next/link";

import { listPublicRecipes } from "@mimos/api-client";

import { publicApi } from "@/lib/server-api";

/**
 * The public recipe library — server-rendered for SEO, no login required.
 * Browsing is cached and revalidated periodically; searches stay fresh.
 */
export default async function LibraryPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = await searchParams;
  const result = await listPublicRecipes({ client: publicApi, query: q ? { q } : {} });
  const recipes = result.data ?? [];

  return (
    <>
      <p>
        <Link href="/">← Mimos</Link>
      </p>
      <h1>Recipe library</h1>
      <p className="muted">Free recipes with everything needed to actually cook them.</p>

      <form className="search" action="/recipes" method="get">
        <input
          type="search"
          name="q"
          defaultValue={q ?? ""}
          placeholder="Search recipes…"
          aria-label="Search the library"
        />
        <button className="button" type="submit">
          Search
        </button>
      </form>

      {recipes.length === 0 ? (
        <div className="card">
          <p>{q ? `Nothing matches “${q}”.` : "The library is empty."}</p>
        </div>
      ) : (
        <ul className="recipe-cards">
          {recipes.map((recipe) => (
            <li key={recipe.id} className="card recipe-card">
              <h2>
                <Link href={`/recipes/${recipe.slug}`}>{recipe.title}</Link>
              </h2>
              <p>{recipe.description}</p>
              <p className="muted">
                {recipe.servings} servings
                {recipe.nutrition?.calories != null ? ` · ~${Math.round(recipe.nutrition.calories)} kcal/serving` : ""}
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
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
