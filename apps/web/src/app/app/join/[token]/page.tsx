"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { joinHousehold, previewHouseholdInvite, type HouseholdInvitePreview } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { apiClient } from "@/lib/api";
import { householdName, listNames } from "@/lib/household";

type Preview = { state: "loading" } | { state: "invalid"; message: string } | { state: "ok"; data: HouseholdInvitePreview };

const INVALID = "This invite link is not valid. Ask for a new one.";

/**
 * Joining a household from an invite link (ADR-0019): who is in it, then
 * exactly what joining does to what the visitor has now, then a confirm.
 * A visitor who is not signed in signs in (or registers) and comes back
 * here.
 */
export default function JoinPage() {
  const { user, signIn } = useAuth();
  const params = useParams<{ token: string }>();
  const router = useRouter();
  const [preview, setPreview] = useState<Preview>({ state: "loading" });
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  const token = params.token ? decodeURIComponent(params.token) : "";
  const subject = user?.profile.sub ?? null;
  useEffect(() => {
    if (!subject || !token) {
      return;
    }
    let cancelled = false;
    void previewHouseholdInvite({ client: apiClient, body: { token } }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.data) {
        setPreview({ state: "ok", data: result.data });
        return;
      }
      const problem = result.error as { detail?: string } | undefined;
      const status = result.response?.status;
      setPreview({ state: "invalid", message: status === 410 && problem?.detail ? problem.detail : INVALID });
    });
    return () => {
      cancelled = true;
    };
  }, [subject, token]);

  if (!user) {
    return (
      <>
        <PageHeader eyebrow="Household" title="You have an invite" />
        <p>Someone invited you to share their household on Mimos. Sign in, or create an account, to see it.</p>
        <button className="button" onClick={() => void signIn(`/app/join/${encodeURIComponent(token)}`)}>
          Sign in
        </button>
      </>
    );
  }

  if (preview.state === "loading") {
    return (
      <>
        <PageHeader eyebrow="Household" title="Invite" />
        <p className="muted">Loading the invite…</p>
      </>
    );
  }

  if (preview.state === "invalid") {
    return (
      <>
        <PageHeader eyebrow="Household" title="This invite cannot be used" />
        <p className="card error" role="alert">
          {preview.message}
        </p>
        <p>
          <Link href="/app">Go to your kitchen</Link>
        </p>
      </>
    );
  }

  const { members, alreadyMember, currentHouseholdShared, expiresAt } = preview.data;
  const names = members.map((member) => member.displayName);

  if (alreadyMember) {
    return (
      <>
        <PageHeader eyebrow="Household" title="You are already in this household" />
        <p>
          <Link href="/app/household">See your household</Link>
        </p>
      </>
    );
  }

  const join = async () => {
    setJoining(true);
    setJoinError(null);
    const result = await joinHousehold({ client: apiClient, body: { token } });
    if (!result.data) {
      setJoining(false);
      const problem = result.error as { detail?: string } | undefined;
      setJoinError(problem?.detail ?? "Could not join the household.");
      return;
    }
    router.push("/app/household");
  };

  return (
    <>
      <PageHeader eyebrow="Household" title={`Join ${householdName(names)}?`} />

      <section className="card" aria-labelledby="what-heading">
        <h2 id="what-heading">What happens when you join</h2>
        <p>You will share recipes, ingredients, meal plans, shopping lists and plugins with {listNames(names)}.</p>
        {currentHouseholdShared ? (
          <ul className="consequences">
            <li>
              You leave the household you are in now. Its recipes, ingredients, meal plans and shopping lists stay
              with it, including the ones you added.
            </li>
            <li>You stop eating the meals planned there, and your logged meals no longer link to its recipes.</li>
            <li>Your food log stays yours. Nobody else sees it.</li>
          </ul>
        ) : (
          <ul className="consequences">
            <li>Your recipes and your own ingredients come with you, and everyone in the household can change them.</li>
            <li>Your meal plans, shopping lists and plugin settings are deleted. You use the household&apos;s instead.</li>
            <li>Your food log stays yours. Nobody else sees it.</li>
          </ul>
        )}
        <p className="muted">
          This invite works until{" "}
          {new Date(expiresAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}.
        </p>
      </section>

      {joinError && (
        <p className="card error" role="alert">
          {joinError}
        </p>
      )}
      <div className="button-row">
        <button className="button" onClick={() => void join()} disabled={joining}>
          {joining ? "Joining…" : "Join household"}
        </button>
        <Link className="button secondary" href="/app">
          Not now
        </Link>
      </div>
    </>
  );
}
