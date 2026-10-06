"use client";

import { useEffect, useState } from "react";

import {
  createHouseholdInvite,
  getHousehold,
  leaveHousehold,
  type Household,
  type HouseholdInvite,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { apiClient } from "@/lib/api";
import { appBaseUrl } from "@/lib/config";
import { inviteLink } from "@/lib/household";

type Load<T> = { state: "loading" } | { state: "error" } | { state: "ok"; data: T };

/**
 * Your household (ADR-0019): who is in it, a single-use invite link for
 * someone new, and, when it is shared, leaving it after a confirmation
 * that says what stays and what goes.
 */
export default function HouseholdPage() {
  const { user, signIn } = useAuth();
  const [household, setHousehold] = useState<Load<Household>>({ state: "loading" });
  const [invite, setInvite] = useState<HouseholdInvite | null>(null);
  const [inviting, setInviting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [copied, setCopied] = useState<"yes" | "failed" | null>(null);
  const [confirmingLeave, setConfirmingLeave] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);

  const subject = user?.profile.sub ?? null;
  useEffect(() => {
    if (!subject) {
      return;
    }
    let cancelled = false;
    void getHousehold({ client: apiClient }).then((result) => {
      if (!cancelled) {
        setHousehold(result.data ? { state: "ok", data: result.data } : { state: "error" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [subject]);

  if (!user) {
    return (
      <>
        <PageHeader title="Household" />
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const createInvite = async () => {
    setInviting(true);
    setInviteError(null);
    setCopied(null);
    const result = await createHouseholdInvite({ client: apiClient });
    setInviting(false);
    if (!result.data) {
      setInviteError("Could not create an invite.");
      return;
    }
    setInvite(result.data);
  };

  const link = invite ? inviteLink(appBaseUrl, invite.token) : "";
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied("yes");
    } catch {
      setCopied("failed");
    }
  };

  const leave = async () => {
    setLeaving(true);
    setLeaveError(null);
    const result = await leaveHousehold({ client: apiClient });
    setLeaving(false);
    if (!result.data) {
      const problem = result.error as { detail?: string } | undefined;
      setLeaveError(problem?.detail ?? "Could not leave the household.");
      return;
    }
    setConfirmingLeave(false);
    setInvite(null);
    setHousehold({ state: "ok", data: result.data });
  };

  const shared = household.state === "ok" && household.data.members.length > 1;

  return (
    <>
      <PageHeader eyebrow="Account" title="Household" />

      <section className="card" aria-labelledby="about-heading">
        <h2 id="about-heading">Sharing a kitchen</h2>
        <p>
          Everyone in a household shares its recipes, ingredients, meal plans, shopping lists and plugins. Each
          person&apos;s food log stays their own.
        </p>
      </section>

      <section className="household-section" aria-labelledby="members-heading">
        <h2 id="members-heading">Who is in it</h2>
        {household.state === "loading" && <p className="muted">Loading your household…</p>}
        {household.state === "error" && (
          <p className="card error" role="alert">
            Could not load your household.
          </p>
        )}
        {household.state === "ok" && (
          <>
            <ul className="member-list">
              {household.data.members.map((member) => (
                <li key={member.id}>
                  {member.displayName}
                  {member.you && <span className="muted">You</span>}
                </li>
              ))}
            </ul>
            {!shared && <p className="muted">It is just you for now.</p>}
          </>
        )}
      </section>

      <section className="household-section" aria-labelledby="invite-heading">
        <h2 id="invite-heading">Invite someone</h2>
        <p>
          Create a link and send it to the person you want to share with. It works once, for seven days, and they
          choose whether to join.
        </p>
        <button className="button" onClick={() => void createInvite()} disabled={inviting}>
          {inviting ? "Creating…" : invite ? "Create another link" : "Create invite link"}
        </button>
        {inviteError && (
          <p className="card error" role="alert">
            {inviteError}
          </p>
        )}
        {invite && (
          <div className="invite">
            <label>
              Invite link
              <input type="text" readOnly value={link} onFocus={(event) => event.target.select()} />
            </label>
            <div className="button-row">
              <button className="button secondary" onClick={() => void copy()}>
                Copy link
              </button>
              <span className="muted" role="status">
                {copied === "yes" && "Copied."}
                {copied === "failed" && "Could not copy. Select the link and copy it yourself."}
              </span>
            </div>
            <p className="muted">
              Works once, until {new Date(invite.expiresAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}.
            </p>
          </div>
        )}
      </section>

      {shared && (
        <section className="household-section" aria-labelledby="leave-heading">
          <h2 id="leave-heading">Leave the household</h2>
          {!confirmingLeave ? (
            <button className="button secondary" onClick={() => setConfirmingLeave(true)}>
              Leave household
            </button>
          ) : (
            <div className="card" role="group" aria-labelledby="leave-confirm-heading">
              <h3 id="leave-confirm-heading">Leave this household?</h3>
              <ul className="consequences">
                <li>
                  The household keeps its recipes, ingredients, meal plans and shopping lists, including the ones you
                  added. Export your data first if you want a copy.
                </li>
                <li>You stop eating the meals planned there, and a meal only you eat is removed.</li>
                <li>You keep your food log, but your logged meals no longer link to the household&apos;s recipes.</li>
                <li>You start again in a household of your own.</li>
              </ul>
              {leaveError && (
                <p className="card error" role="alert">
                  {leaveError}
                </p>
              )}
              <div className="button-row">
                <button className="button danger" onClick={() => void leave()} disabled={leaving}>
                  {leaving ? "Leaving…" : "Leave household"}
                </button>
                <button className="button secondary" onClick={() => setConfirmingLeave(false)} disabled={leaving}>
                  Stay
                </button>
              </div>
            </div>
          )}
        </section>
      )}
    </>
  );
}
