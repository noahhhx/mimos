"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

import { getRecipeCollection, type RecipeCollection } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { RecipeTabForm } from "@/components/recipe-tab-form";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { apiClient } from "@/lib/api";

export default function EditRecipeTabPage() {
  const { user } = useAuth();
  const params = useParams<{ id: string }>();
  const [collection, setCollection] = useState<RecipeCollection | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user || !params.id) {
      return;
    }
    let cancelled = false;
    void getRecipeCollection({ client: apiClient, path: { collectionId: params.id } }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.error || !result.data) {
        setError(result.response?.status === 404 ? "This tab no longer exists." : "The API rejected the request.");
        return;
      }
      setCollection(result.data);
    });
    return () => {
      cancelled = true;
    };
  }, [user, params.id]);

  if (!user) {
    return (
      <>
        <PageHeader title="Edit tab" />
        <SignInPrompt>You need to sign in to edit tabs.</SignInPrompt>
      </>
    );
  }

  return (
    <>
      <p className="back">
        <Link href={collection ? `/app/recipes?tab=${collection.id}` : "/app/recipes"}>← Recipes</Link>
      </p>
      <PageHeader eyebrow="Edit tab" title={collection?.name ?? "Tab"} />
      {error ? (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      ) : collection === null ? (
        <p className="muted">Loading…</p>
      ) : (
        <RecipeTabForm initial={collection} />
      )}
    </>
  );
}
