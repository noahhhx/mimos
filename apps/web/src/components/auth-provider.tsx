"use client";

import { User } from "oidc-client-ts";
import { createContext, useCallback, useContext, useEffect, useState } from "react";

import { userManager } from "@/lib/auth";

type AuthState = {
  user: User | null;
  signIn: () => Promise<void>;
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
    void userManager.getUser().then(setUser);
    return () => {
      userManager.events.removeUserLoaded(onLoaded);
      userManager.events.removeUserUnloaded(onUnloaded);
    };
  }, []);

  const signIn = useCallback(async () => {
    await userManager.signinRedirect();
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
