"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { PageHeader } from "@/components/page-header";
import { userManager } from "@/lib/auth";
import { returnPath } from "@/lib/return-to";

/**
 * The OIDC redirect target: finishes the authorization code exchange, then
 * returns to the page that started sign-in, or the Kitchen. Also serves as
 * the silent-renew target.
 */
export default function CallbackPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    userManager
      .signinCallback()
      .then((user) => {
        if (!cancelled) {
          router.replace(returnPath(user?.state));
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
        <PageHeader title="Sign-in failed" />
        <div className="card" role="alert">
          <p>{error}</p>
        </div>
      </>
    );
  }
  return <p className="muted">Finishing sign-in…</p>;
}
