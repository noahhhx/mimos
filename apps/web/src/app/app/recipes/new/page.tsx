"use client";

import Link from "next/link";

import { RecipeForm } from "@/components/recipe-form";
import { useAuth } from "@/components/auth-provider";

/** Write a new personal recipe with the same richness as the library. */
export default function NewRecipePage() {
  const { user, signIn } = useAuth();

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
      <p>
        <Link href="/app/recipes">← Recipes</Link>
      </p>
      <h1>New recipe</h1>
      <RecipeForm />
    </>
  );
}
