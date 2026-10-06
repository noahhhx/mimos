"use client";

import { User } from "oidc-client-ts";
import { createContext, useCallback, useContext, useEffect, useState } from "react";

import { currentUser, userManager } from "@/lib/auth";
import type { SignInState } from "@/lib/return-to";

type AuthState = {
  user: User | null;
  /** Signs in through Keycloak, then returns to `returnTo` (an app path) or the Kitchen. */
  signIn: (returnTo?: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

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
    await userManager.signinRedirect({ state });
  }, []);

  const signOut = useCallback(async () => {
    await userManager.signoutRedirect();
  }, []);

  return <AuthContext.Provider value={{ user, signIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (context === null) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}
