"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { userManager } from "@/lib/auth";

/**
 * The OIDC redirect target: finishes the authorization code exchange, then
 * returns to the app. Also serves as the silent-renew target.
 */
export default function CallbackPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    userManager
      .signinCallback()
      .then(() => {
        if (!cancelled) {
          router.replace("/app");
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Sign-in failed.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [router]);

  if (error) {
    return (
      <>
        <h1>Sign-in failed</h1>
        <div className="card" role="alert">
          <p>{error}</p>
        </div>
      </>
    );
  }
  return <p className="muted">Finishing sign-in…</p>;
}
