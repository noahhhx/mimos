"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";

import { getMe, type UserProfile } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { apiClient } from "@/lib/api";

type Load<T> = { state: "loading" } | { state: "error" } | { state: "ok"; data: T };

/**
 * The signed-in user's menu at the header's right: a square with a user
 * icon that opens their name, member-since date, "Household" (ADR-0019),
 * "Plugins" (ADR-0013),
 * "Your data" and "Sign out". Renders nothing when signed out. A
 * disclosure, not an ARIA menu: Escape or a click outside closes it.
 */
export function ProfileMenu() {
  const { user, signOut } = useAuth();
  const [profile, setProfile] = useState<Load<UserProfile>>({ state: "loading" });
  const [open, setOpen] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  const subject = user?.profile.sub ?? null;
  useEffect(() => {
    if (!subject) {
      return;
    }
    let cancelled = false;
    void getMe({ client: apiClient }).then((result) => {
      if (!cancelled) {
        setProfile(result.data ? { state: "ok", data: result.data } : { state: "error" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [subject]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !container.current?.contains(event.target)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        toggle.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  if (!user) {
    return null;
  }

  return (
    <div className="profile-menu" ref={container}>
      <button
        ref={toggle}
        className="profile-toggle"
        type="button"
        aria-label="Profile"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
      >
        <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor">
          <circle cx="8" cy="5" r="3" />
          <path d="M2 15c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" />
        </svg>
      </button>
      {open && (
        <div id={panelId} className="profile-panel">
          <div className="profile-who">
            {profile.state === "error" ? (
              <p className="error" role="alert">
                Could not load your profile.
              </p>
            ) : profile.state === "loading" ? (
              <p className="muted">Loading your profile…</p>
            ) : (
              <>
                <p className="profile-name">{profile.data.displayName}</p>
                <p className="muted">Member since {new Date(profile.data.createdAt).toLocaleDateString()}</p>
              </>
            )}
          </div>
          <Link href="/app/household" onClick={() => setOpen(false)}>
            Household
          </Link>
          <Link href="/app/plugins" onClick={() => setOpen(false)}>
            Plugins
          </Link>
          <Link href="/app/account" onClick={() => setOpen(false)}>
            Your data
          </Link>
          <button type="button" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
