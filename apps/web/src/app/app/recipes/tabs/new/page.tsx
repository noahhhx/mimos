"use client";

import Link from "next/link";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { RecipeTabForm } from "@/components/recipe-tab-form";
import { SignInPrompt } from "@/components/sign-in-prompt";

export default function NewRecipeTabPage() {
  const { user } = useAuth();

  if (!user) {
    return (
      <>
        <PageHeader title="New tab" />
        <SignInPrompt>You need to sign in to make tabs.</SignInPrompt>
      </>
    );
  }

  return (
    <>
      <p className="back">
        <Link href="/app/recipes">← Recipes</Link>
      </p>
      <PageHeader title="New tab" />
      <RecipeTabForm />
    </>
  );
}
