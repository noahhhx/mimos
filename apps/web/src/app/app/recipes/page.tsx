"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import {
  clearDefaultRecipeCollection,
  listLibraryRecipes,
  listMyRecipes,
  listRecipeCollectionRecipes,
  listRecipeCollections,
  setDefaultRecipeCollection,
  type RecipeCollection,
  type RecipeSummary,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { RecipeList } from "@/components/recipe-list";
import { TabCoverImage } from "@/components/tab-cover";
import { apiClient } from "@/lib/api";
import {
  DEFAULT_RECIPE_SORT,
  RECIPE_SORTS,
  orderRecipes,
  parseRecipeSort,
  recipeTags,
  type RecipeSort,
} from "@/lib/recipe-order";
import { sameTab, tabFromParam, tabParam, type RecipeTab } from "@/lib/recipe-tabs";

/**
 * The recipe book: your own recipes and the curated library side by side,
 * both searchable, plus the household's own tabs (ADR-0036). Personal
 * recipes are edited here; library recipes link to their public pages.
 */
export default function RecipesPage() {
  // useSearchParams needs a boundary, or the build cannot prerender the page.
  return (
    <Suspense fallback={<PageHeader eyebrow="Your recipe book" title="Recipes" />}>
      <RecipeBook />
    </Suspense>
  );
}

function RecipeBook() {
  const SORT_KEY = "mimos.recipes.sort";

  const { user } = useAuth();
  const router = useRouter();
  const param = useSearchParams().get("tab");
  const [collections, setCollections] = useState<RecipeCollection[] | null>(null);
  const [query, setQuery] = useState("");
  const [mine, setMine] = useState<RecipeSummary[] | null>(null);
  const [library, setLibrary] = useState<RecipeSummary[] | null>(null);
  const [members, setMembers] = useState<{ key: string; recipes: RecipeSummary[] } | null>(null);
  const [sort, setSort] = useState<RecipeSort>(DEFAULT_RECIPE_SORT);
  const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const tab: RecipeTab | null = collections === null ? null : tabFromParam(param, collections);
  const openId = tab?.kind === "collection" ? tab.id : undefined;
  const membersKey = openId === undefined ? undefined : `${openId}\n${query}`;

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
    void listRecipeCollections({ client: apiClient }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.error) {
        setError("The API rejected the request.");
      }
      // Without its tabs the page still works as it did before tabs existed.
      setCollections(result.data ?? []);
    });
    return () => {
      cancelled = true;
    };
  }, [user]);

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

  useEffect(() => {
    if (!user || openId === undefined || membersKey === undefined) {
      return;
    }
    let cancelled = false;
    void listRecipeCollectionRecipes({
      client: apiClient,
      path: { collectionId: openId },
      query: query ? { q: query } : {},
    }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.error) {
        setError("The API rejected the request.");
        return;
      }
      setMembers({ key: membersKey, recipes: result.data ?? [] });
    });
    return () => {
      cancelled = true;
    };
  }, [user, openId, query, membersKey]);

  if (!user) {
    return (
      <>
        <PageHeader title="Recipes" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
      </>
    );
  }

  const open = (next: RecipeTab) => router.push(`/app/recipes?tab=${tabParam(next)}`, { scroll: false });
  const opened = openId === undefined ? undefined : collections?.find((collection) => collection.id === openId);

  const toggleDefault = async (collection: RecipeCollection) => {
    const on = !collection.isDefault;
    const path = { collectionId: collection.id };
    const result = on
      ? await setDefaultRecipeCollection({ client: apiClient, path })
      : await clearDefaultRecipeCollection({ client: apiClient, path });
    if (result.error) {
      setError("The API rejected the request.");
      return;
    }
    // A page that landed here as the default would leave once it is not, so the URL names the tab.
    if (param !== collection.id) {
      router.replace(`/app/recipes?tab=${collection.id}`, { scroll: false });
    }
    setCollections((current) =>
      (current ?? []).map((each) => ({
        ...each,
        isDefault: each.id === collection.id ? on : on ? false : each.isDefault,
      })),
    );
  };

  const tabRecipes =
    tab?.kind === "mine"
      ? mine
      : tab?.kind === "library"
        ? library
        : members !== null && members.key === membersKey
          ? members.recipes
          : null;
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

  const tabButton = (target: RecipeTab, label: string) => {
    const active = tab !== null && sameTab(tab, target);
    return (
      <button
        key={tabParam(target)}
        type="button"
        role="tab"
        aria-selected={active}
        className={active ? "active" : undefined}
        onClick={() => open(target)}
      >
        {label}
      </button>
    );
  };

  const emptyCopy =
    activeTags.size > 0
      ? "No recipes have all of these tags."
      : tab?.kind === "mine"
        ? "No personal recipes yet. Write your first one, or cook from the library."
        : tab?.kind === "collection"
          ? query
            ? `Nothing in this tab matches “${query}”.`
            : "Nothing in this tab yet. Edit the tab to pick recipes or gather them by tag."
          : query
            ? `Nothing in the library matches “${query}”.`
            : "The library is empty.";

  return (
    <>
      <PageHeader
        eyebrow="Your recipe book"
        title="Recipes"
        actions={
          <>
            <Link href="/app/recipes/tabs/new" className="button secondary">
              + New tab
            </Link>
            <Link href="/app/recipes/new" className="button">
              + New recipe
            </Link>
          </>
        }
      />

      <div className="tabs" role="tablist">
        {collections !== null && collections.length > 0 && tabButton({ kind: "gallery" }, "Tabs")}
        {tabButton({ kind: "mine" }, `My recipes (${mine?.length ?? "…"})`)}
        {tabButton({ kind: "library" }, `Library (${library?.length ?? "…"})`)}
        {collections?.map((collection) => tabButton({ kind: "collection", id: collection.id }, collection.name))}
      </div>

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {tab === null ? (
        <p className="muted">Loading…</p>
      ) : tab.kind === "gallery" ? (
        <TabGallery collections={collections ?? []} />
      ) : (
        <>
          {opened && (
            <p className="button-row">
              <Link href={`/app/recipes/tabs/${opened.id}`} className="button secondary small">
                Edit tab
              </Link>
              <button
                type="button"
                className={opened.isDefault ? "button small" : "button secondary small"}
                aria-pressed={opened.isDefault}
                onClick={() => void toggleDefault(opened)}
              >
                Open by default
              </button>
            </p>
          )}

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
                    className={active ? "button small tag-filter" : "button secondary small tag-filter"}
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

          {displayedRecipes === null ? (
            <p className="muted">Loading…</p>
          ) : displayedRecipes.length === 0 ? (
            <div className="card">
              <p>{emptyCopy}</p>
            </div>
          ) : (
            <RecipeList
              recipes={displayedRecipes}
              hrefOf={(recipe) => (recipe.slug ? `/recipes/${recipe.slug}` : `/app/recipes/${recipe.id}`)}
            />
          )}
        </>
      )}
    </>
  );
}

/** The household's tabs as tiles, each opening its tab, and a last tile for a new one. */
function TabGallery({ collections }: { collections: RecipeCollection[] }) {
  return (
    <ul className="tab-gallery" aria-label="Tabs">
      {collections.map((collection) => (
        <li key={collection.id}>
          <Link href={`/app/recipes?tab=${collection.id}`} className="tab-tile">
            <span className="tab-tile-cover" aria-hidden="true">
              {collection.pictureId ? (
                <TabCoverImage collectionId={collection.id} pictureId={collection.pictureId} alt="" />
              ) : (
                <span className="tab-tile-blank">{collection.name}</span>
              )}
            </span>
            <span className="tab-tile-name">{collection.name}</span>
            <span className="tab-tile-meta">
              {collection.recipeCount === 1 ? "1 recipe" : `${collection.recipeCount} recipes`}
              {collection.isDefault && <span className="tag">Default</span>}
            </span>
          </Link>
        </li>
      ))}
      <li>
        <Link href="/app/recipes/tabs/new" className="tab-tile">
          <span className="tab-tile-cover tab-tile-new" aria-hidden="true">
            +
          </span>
          <span className="tab-tile-name">New tab</span>
          <span className="tab-tile-meta">Pick recipes, or gather them by tag</span>
        </Link>
      </li>
    </ul>
  );
}
