"use client";

import { useSyncExternalStore } from "react";

import { useAuth } from "@/components/auth-provider";
import { signupPolicy } from "@/lib/config";

const noSubscription = () => () => {};

/**
 * Create account, unless this instance closed sign-up (MIMOS_SIGNUP) or the
 * visitor is already signed in. Pages are prerendered with the build's
 * config while the browser reads the container's, so the server renders
 * nothing and the browser decides after hydration.
 */
export function CreateAccountButton({ returnTo, primary = false }: { returnTo?: string; primary?: boolean }) {
  const { user, signUp } = useAuth();
  const offered = useSyncExternalStore(
    noSubscription,
    () => signupPolicy === "open",
    () => false,
  );
  if (!offered || user) {
    return null;
  }
  return (
    <button className={primary ? "button" : "button secondary"} onClick={() => void signUp(returnTo)}>
      Create account
    </button>
  );
}
