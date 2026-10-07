"use client";

import { User } from "oidc-client-ts";
import { createContext, useCallback, useContext, useEffect, useState } from "react";

import { currentUser, userManager } from "@/lib/auth";
import type { SignInState } from "@/lib/return-to";

type AuthState = {
  user: User | null;
  /** Signs in through Keycloak, then returns to `returnTo` (an app path) or the Kitchen. */
  signIn: (returnTo?: string) => Promise<void>;
  /**
   * Opens Keycloak's registration form, then returns like `signIn`. Every
   * Create account calls this, so a paid sign-up replaces only this (ADR-0020).
   */
  signUp: (returnTo?: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

/**
 * Keycloak's pages cannot read the app's stored theme, so every sign-in
 * carries the one on screen (ADR-0021). Light is sent too, so it replaces
 * a dark choice made earlier on Keycloak's own pages.
 */
function themeParam(): { mimos_theme: "light" | "dark" } {
  return { mimos_theme: document.documentElement.dataset.theme === "dark" ? "dark" : "light" };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const onLoaded = (loaded: User) => setUser(loaded);
    const onUnloaded = () => setUser(null);
    userManager.events.addUserLoaded(onLoaded);
    userManager.events.addUserUnloaded(onUnloaded);
    // Not getUser(): a stored session can be long expired, and showing it as
    // signed in would only lead to 401s.
    void currentUser().then(setUser);
    return () => {
      userManager.events.removeUserLoaded(onLoaded);
      userManager.events.removeUserUnloaded(onUnloaded);
    };
  }, []);

  const signIn = useCallback(async (returnTo?: string) => {
    const state: SignInState | undefined = returnTo ? { returnTo } : undefined;
    await userManager.signinRedirect({ state, extraQueryParams: themeParam() });
  }, []);

  const signUp = useCallback(async (returnTo?: string) => {
    const state: SignInState | undefined = returnTo ? { returnTo } : undefined;
    await userManager.signinRedirect({ state, prompt: "create", extraQueryParams: themeParam() });
  }, []);

  const signOut = useCallback(async () => {
    await userManager.signoutRedirect();
  }, []);

  return <AuthContext.Provider value={{ user, signIn, signUp, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}
