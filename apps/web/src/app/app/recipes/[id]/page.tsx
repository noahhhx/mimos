"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { clearRecipeRating, deleteRecipe, getRecipe, rateRecipe, type RecipeDetail } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { RecipeForm } from "@/components/recipe-form";
import { RecipeView } from "@/components/recipe-view";
import { StarRating } from "@/components/star-rating";
import { apiClient } from "@/lib/api";
import { parseTrail, readCookState, recipeHref } from "@/lib/cook-trail";
import { nextRating } from "@/lib/rating";

/**
 * One recipe: the cook's view by default, with edit and delete for your
 * own recipes. Library recipes are read-only (with a link to their public
 * page).
 */
export default function RecipeDetailPage() {
  const { user } = useAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [recipe, setRecipe] = useState<RecipeDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [trail, setTrail] = useState<{ id: string; title: string }[]>([]);

  useEffect(() => {
    // Read ?via= in an effect, as the plan page reads ?week=, rather than useSearchParams.
    const ids = parseTrail(new URLSearchParams(window.location.search).get("via"), params.id);
    setTrail(
      ids.map((id) => {
        let title: string | undefined;
        try {
          title = readCookState(window.sessionStorage, id)?.title;
        } catch {
          // Storage unavailable: the crumb is still a link, just unnamed.
        }
        return { id, title: title || "Recipe" };
      }),
    );
  }, [params.id]);

  useEffect(() => {
    if (!user || !params.id) {
      return;
    }
    let cancelled = false;
    void getRecipe({ client: apiClient, path: { recipeId: params.id } }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.error) {
        if (result.response?.status === 404) {
          setNotFound(true);
        } else {
          setError("The API rejected the request.");
        }
        return;
      }
      setRecipe(result.data ?? null);
    });
    return () => {
      cancelled = true;
    };
  }, [user, params.id]);

  if (!user) {
    return (
      <>
        <PageHeader title="Recipe" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
      </>
    );
  }

  if (notFound) {
    return (
      <>
        <p>
          <Link href="/app/recipes">← Recipes</Link>
        </p>
        <PageHeader title="Recipe not found" />
        <p className="muted">It may have been deleted, or it belongs to someone else.</p>
      </>
    );
  }

  // Until the followed recipe loads, the trail would name the one being left.
  const shownTrail = recipe?.id === params.id ? trail : [];

  const rate = async (star: number) => {
    if (!recipe) {
      return;
    }
    const next = nextRating(recipe.rating, star);
    if (next === null) {
      const result = await clearRecipeRating({ client: apiClient, path: { recipeId: recipe.id } });
      if (result.error) {
        setError("The API rejected the request.");
        return;
      }
      setError(null);
      setRecipe({ ...recipe, rating: undefined });
      return;
    }
    const result = await rateRecipe({ client: apiClient, path: { recipeId: recipe.id }, body: { stars: next } });
    if (result.error || !result.data) {
      setError("The API rejected the request.");
      return;
    }
    setError(null);
    setRecipe(result.data);
  };

  const remove = async () => {
    if (!recipe || !confirm(`Delete “${recipe.title}”? This cannot be undone.`)) {
      return;
    }
    const result = await deleteRecipe({ client: apiClient, path: { recipeId: recipe.id } });
    if (result.error) {
      setError("The API rejected the request.");
      return;
    }
    router.push("/app/recipes");
  };

  return (
    <>
      {recipe && shownTrail.length > 0 ? (
        <nav className="back trail" aria-label="Recipes followed">
          <ol>
            <li>
              <Link href="/app/recipes">Recipes</Link>
            </li>
            {shownTrail.map((crumb, index) => (
              <li key={crumb.id}>
                <Link
                  href={recipeHref(
                    crumb.id,
                    shownTrail.slice(0, index).map((c) => c.id),
                  )}
                >
                  {crumb.title}
                </Link>
              </li>
            ))}
            <li aria-current="page">{recipe.title}</li>
          </ol>
        </nav>
      ) : (
        <p className="back">
          <Link href="/app/recipes">← Recipes</Link>
        </p>
      )}

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {!recipe ? (
        <p className="muted">Loading…</p>
      ) : editing ? (
        <>
          <PageHeader title="Edit recipe" />
          <RecipeForm
            initial={recipe}
            onSaved={(saved) => {
              setRecipe(saved);
              setEditing(false);
            }}
            onCancel={() => setEditing(false)}
          />
        </>
      ) : (
        <>
          <div className="toolbar">
            <span className="muted">{whose(recipe)}</span>
            <div className="actions">
              {recipe.isLibrary ? (
                <>
                  {recipe.slug && (
                    <Link className="button secondary" href={`/recipes/${recipe.slug}`}>
                      Public page
                    </Link>
                  )}
                  <span className="muted">Library recipes are read-only.</span>
                </>
              ) : (
                <>
                  <button type="button" className="button secondary" onClick={() => setEditing(true)}>
                    Edit
                  </button>
                  <button type="button" className="button danger" onClick={() => void remove()}>
                    Delete
                  </button>
                </>
              )}
            </div>
          </div>
          {!recipe.isLibrary && (
            <p className="rating-row">
              <StarRating rating={recipe.rating} onChange={(star) => void rate(star)} />
            </p>
          )}
          {/* Keyed so following an ingredient link to another recipe loads that recipe's own ticks. */}
          <RecipeView key={recipe.id} recipe={recipe} via={shownTrail.map((crumb) => crumb.id)} />
        </>
      )}
    </>
  );
}

/** Whose recipe this is, above its actions. */
function whose(recipe: RecipeDetail): string {
  if (recipe.isLibrary) {
    return "Library recipe";
  }
  return recipe.createdBy && !recipe.createdBy.you ? `Added by ${recipe.createdBy.displayName}` : "Your recipe";
}
