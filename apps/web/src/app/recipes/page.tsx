import { listPublicRecipes } from "@mimos/api-client";

import { RecipeList } from "@/components/recipe-list";
import { SplitPage } from "@/components/split-page";
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
    <SplitPage
      rail={
        <>
          <header className="page-header">
            <p className="eyebrow">Library</p>
            <h1>Recipes</h1>
          </header>
          <p className="lede">Free recipes with everything needed to actually cook them.</p>
        </>
      }
    >
      <form className="search" action="/recipes" method="get">
        <input
          type="search"
          name="q"
          defaultValue={q ?? ""}
          placeholder="What do you feel like?"
          aria-label="Search the library"
        />
        <button className="button secondary" type="submit">
          Search
        </button>
      </form>

      {recipes.length === 0 ? (
        <div className="card">
          <p>{q ? `Nothing matches “${q}”.` : "The library is empty."}</p>
        </div>
      ) : (
        <RecipeList recipes={recipes} hrefOf={(recipe) => `/recipes/${recipe.slug}`} />
      )}
    </SplitPage>
  );
}
