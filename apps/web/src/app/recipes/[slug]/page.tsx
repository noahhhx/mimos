import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";

import { getPublicRecipe } from "@mimos/api-client";

import { RecipeView } from "@/components/recipe-view";
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
    <>
      <p className="back">
        <Link href="/recipes">← Library</Link>
      </p>
      <RecipeView recipe={recipe}>
        <p className="muted recipe-cta">
          Cook this every week? <Link href="/app">Plan it in Mimos</Link> — the shopping list builds itself.
        </p>
      </RecipeView>
    </>
  );
}
