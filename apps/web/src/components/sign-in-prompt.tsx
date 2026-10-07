"use client";

import { useAuth } from "@/components/auth-provider";
import { CreateAccountButton } from "@/components/create-account-button";

/** What a page of the app shows a signed-out visitor: why to sign in, and the way in. */
export function SignInPrompt({ children, returnTo }: { children: React.ReactNode; returnTo?: string }) {
  const { signIn } = useAuth();
  return (
    <>
      <p>{children}</p>
      <p className="cta">
        <button className="button" onClick={() => void signIn(returnTo)}>
          Sign in
        </button>
        <CreateAccountButton returnTo={returnTo} />
      </p>
    </>
  );
}
