"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { RecipeForm } from "@/components/recipe-form";
import { useAuth } from "@/components/auth-provider";

/** Write a new personal recipe with the same richness as the library. */
export default function NewRecipePage() {
  const { user, signIn } = useAuth();
  const router = useRouter();

  if (!user) {
    return (
      <>
        <h1>New recipe</h1>
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
      <h1>New recipe</h1>
      <RecipeForm onCancel={() => router.push("/app/recipes")} />
    </>
  );
}
