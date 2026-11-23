"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { listLibraryRecipes, listMyRecipes, type RecipeSummary } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { RecipeList } from "@/components/recipe-list";
import { apiClient } from "@/lib/api";
import {
  DEFAULT_RECIPE_SORT,
  RECIPE_SORTS,
  orderRecipes,
  parseRecipeSort,
  recipeTags,
  type RecipeSort,
} from "@/lib/recipe-order";

/**
 * The recipe book: your own recipes and the curated library side by side,
 * both searchable. Personal recipes are edited here; library recipes link
 * to their public pages.
 */
export default function RecipesPage() {
  const SORT_KEY = "mimos.recipes.sort";

  const { user } = useAuth();
  const [tab, setTab] = useState<"mine" | "library">("mine");
  const [query, setQuery] = useState("");
  const [mine, setMine] = useState<RecipeSummary[] | null>(null);
  const [library, setLibrary] = useState<RecipeSummary[] | null>(null);
  const [sort, setSort] = useState<RecipeSort>(DEFAULT_RECIPE_SORT);
  const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      setSort(parseRecipeSort(window.localStorage.getItem(SORT_KEY)));
    } catch {
      // Storage can be unavailable; the default stands.
    }
  }, []);

  const chooseSort = (next: RecipeSort) => {
    setSort(next);
    try {
      window.localStorage.setItem(SORT_KEY, next);
    } catch {
      // Not remembered, but the choice still applies.
    }
  };

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
        <PageHeader title="Recipes" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
      </>
    );
  }

  const tabRecipes = tab === "mine" ? mine : library;
  const availableTags = recipeTags(tabRecipes ?? []);
  const activeTags = new Set([...selectedTags].filter((tag) => availableTags.includes(tag)));
  const displayedRecipes = tabRecipes === null ? null : orderRecipes(tabRecipes, sort, activeTags);

  const toggleTag = (tag: string) => {
    setSelectedTags((current) => {
      const next = new Set(current);
      if (next.has(tag)) {
        next.delete(tag);
      } else {
        next.add(tag);
      }
      return next;
    });
  };

  return (
    <>
      <PageHeader
        eyebrow="Your recipe book"
        title="Recipes"
        actions={
          <Link href="/app/recipes/new" className="button">
            + New recipe
          </Link>
        }
      />

      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "mine"}
          className={tab === "mine" ? "active" : undefined}
          onClick={() => setTab("mine")}
        >
          My recipes ({mine?.length ?? "…"})
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "library"}
          className={tab === "library" ? "active" : undefined}
          onClick={() => setTab("library")}
        >
          Library ({library?.length ?? "…"})
        </button>
      </div>

      <div className="recipe-filters">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by title, description, or tag…"
          aria-label="Search recipes"
        />
        <label>
          Sort by
          <select value={sort} onChange={(e) => chooseSort(parseRecipeSort(e.target.value))}>
            {(Object.keys(RECIPE_SORTS) as RecipeSort[]).map((key) => (
              <option key={key} value={key}>
                {RECIPE_SORTS[key].label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {availableTags.length > 0 && (
        // biome-ignore lint/a11y/useSemanticElements: a <fieldset> has the same group role but brings its own border, padding, and form semantics.
        <div className="button-row" role="group" aria-label="Filter by tag">
          {availableTags.map((tag) => {
            const active = activeTags.has(tag);
            return (
              <button
                key={tag}
                type="button"
                className={active ? "button small" : "button secondary small"}
                aria-pressed={active}
                onClick={() => toggleTag(tag)}
              >
                {tag}
              </button>
            );
          })}
          {activeTags.size > 0 && (
            <button type="button" className="button secondary small" onClick={() => setSelectedTags(new Set())}>
              Clear tags
            </button>
          )}
        </div>
      )}

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {displayedRecipes === null ? (
        <p className="muted">Loading…</p>
      ) : displayedRecipes.length === 0 ? (
        <div className="card">
          <p>
            {activeTags.size > 0
              ? "No recipes have all of these tags."
              : tab === "mine"
                ? "No personal recipes yet. Write your first one, or cook from the library."
                : query
                  ? `Nothing in the library matches “${query}”.`
                  : "The library is empty."}
          </p>
        </div>
      ) : (
        <RecipeList
          recipes={displayedRecipes}
          hrefOf={(recipe) =>
            tab === "mine" || !recipe.slug ? `/app/recipes/${recipe.id}` : `/recipes/${recipe.slug}`
          }
        />
      )}
    </>
  );
}
