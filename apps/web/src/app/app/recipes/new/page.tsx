"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { RecipeForm } from "@/components/recipe-form";
import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";

/** Write a new personal recipe with the same richness as the library. */
export default function NewRecipePage() {
  const { user, signIn } = useAuth();
  const router = useRouter();

  if (!user) {
    return (
      <>
        <PageHeader title="New recipe" />
        <p>You need to sign in to write recipes.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  return (
    <>
      <p className="back">
        <Link href="/app/recipes">← Recipes</Link>
      </p>
      <PageHeader title="New recipe" />
      <RecipeForm onCancel={() => router.push("/app/recipes")} />
    </>
  );
}
