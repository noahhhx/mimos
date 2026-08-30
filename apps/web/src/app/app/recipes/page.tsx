"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import {
  listLibraryRecipes,
  listMyRecipes,
  type RecipeSummary,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { apiClient } from "@/lib/api";

/**
 * The recipe book: your own recipes and the curated library side by side,
 * both searchable. Personal recipes are edited here; library recipes link
 * to their public pages.
 */
export default function RecipesPage() {
  const { user, signIn } = useAuth();
  const [tab, setTab] = useState<"mine" | "library">("mine");
  const [query, setQuery] = useState("");
  const [mine, setMine] = useState<RecipeSummary[] | null>(null);
  const [library, setLibrary] = useState<RecipeSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    const load = async () => {
      const results = await Promise.all([
        listMyRecipes({ client: apiClient, query: query ? { q: query } : {} }),
        listLibraryRecipes({ client: apiClient, query: query ? { q: query } : {} }),
      ]);
      if (cancelled) {
        return;
      }
      const failed = results.find((result) => result.error);
      if (failed) {
        setError("The API rejected the request.");
        return;
      }
      setError(null);
      setMine(results[0].data ?? []);
      setLibrary(results[1].data ?? []);
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [user, query]);

  if (!user) {
    return (
      <>
        <h1>Recipes</h1>
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const recipes = tab === "mine" ? mine : library;

  return (
    <>
      <div className="toolbar">
        <h1>Recipes</h1>
        <Link href="/app/recipes/new" className="button">
          + New recipe
        </Link>
      </div>

      <div className="tabs" role="tablist">
        <button
          role="tab"
          aria-selected={tab === "mine"}
          className={tab === "mine" ? "active" : undefined}
          onClick={() => setTab("mine")}
        >
          My recipes ({mine?.length ?? "…"})
        </button>
        <button
          role="tab"
          aria-selected={tab === "library"}
          className={tab === "library" ? "active" : undefined}
          onClick={() => setTab("library")}
        >
          Library ({library?.length ?? "…"})
        </button>
      </div>

      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by title, description, or tag…"
        aria-label="Search recipes"
      />

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {recipes === null ? (
        <p className="muted">Loading…</p>
      ) : recipes.length === 0 ? (
        <div className="card">
          <p>
            {tab === "mine"
              ? "No personal recipes yet — write your first one, or cook from the library."
              : query
                ? `Nothing in the library matches “${query}”.`
                : "The library is empty."}
          </p>
        </div>
      ) : (
        <ul className="recipe-cards">
          {recipes.map((recipe) => (
            <li key={recipe.id} className="card recipe-card">
              <h2>
                {tab === "mine" || !recipe.slug ? (
                  <Link href={`/app/recipes/${recipe.id}`}>{recipe.title}</Link>
                ) : (
                  <Link href={`/recipes/${recipe.slug}`}>{recipe.title}</Link>
                )}
              </h2>
              <p>{recipe.description}</p>
              <p className="muted">
                {recipe.servings} servings
                {recipe.nutrition?.calories != null
                  ? ` · ~${Math.round(recipe.nutrition.calories)} kcal/serving`
                  : ""}
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
