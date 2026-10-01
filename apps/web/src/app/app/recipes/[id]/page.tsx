"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { deleteRecipe, getRecipe, type RecipeDetail } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { RecipeForm } from "@/components/recipe-form";
import { RecipeView } from "@/components/recipe-view";
import { apiClient } from "@/lib/api";

/**
 * One recipe: the cook's view by default, with edit and delete for your
 * own recipes. Library recipes are read-only (with a link to their public
 * page).
 */
export default function RecipeDetailPage() {
  const { user, signIn } = useAuth();
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [recipe, setRecipe] = useState<RecipeDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [notFound, setNotFound] = useState(false);

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
        <h1>Recipe</h1>
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  if (notFound) {
    return (
      <>
        <p>
          <Link href="/app/recipes">← Recipes</Link>
        </p>
        <h1>Recipe not found</h1>
        <p className="muted">It may have been deleted, or it belongs to someone else.</p>
      </>
    );
  }

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
      <p className="back">
        <Link href="/app/recipes">← Recipes</Link>
      </p>

      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      {!recipe ? (
        <p className="muted">Loading…</p>
      ) : editing ? (
        <>
          <h1>Edit recipe</h1>
          <RecipeForm
            initial={recipe}
            onSaved={(saved) => {
              setRecipe(saved);
              setEditing(false);
            }}
          />
        </>
      ) : (
        <>
          <div className="toolbar">
            <span className="muted">Your recipe</span>
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
                  <button className="button secondary" onClick={() => setEditing(true)}>
                    Edit
                  </button>
                  <button className="button danger" onClick={() => void remove()}>
                    Delete
                  </button>
                </>
              )}
            </div>
          </div>
          <RecipeView recipe={recipe} />
        </>
      )}
    </>
  );
}
