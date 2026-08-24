"use client";

import { getMe, type UserProfile } from "@mimos/api-client";
import { useEffect, useState } from "react";

import { useAuth } from "@/components/auth-provider";
import { apiClient } from "@/lib/api";

/**
 * The authenticated home: a real API call through the generated client —
 * the proof that the whole vertical slice works (login → token → API →
 * profile).
 */
export default function AppPage() {
  const { user, signIn, signOut } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    getMe({ client: apiClient }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.error) {
        setError(result.error.detail ?? "The API rejected the request.");
      } else if (result.data) {
        setProfile(result.data);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [user]);

  if (!user) {
    return (
      <>
        <h1>Mimos</h1>
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  return (
    <>
      <div className="toolbar">
        <h1>Your kitchen</h1>
        <button className="button" onClick={() => void signOut()}>
          Sign out
        </button>
      </div>

      {error && (
        <div className="card" role="alert">
          <p>{error}</p>
        </div>
      )}

      {profile ? (
        <div className="card">
          <h2>Profile</h2>
          <dl className="profile">
            <dt>Name</dt>
            <dd>{profile.displayName}</dd>
            <dt>Subject</dt>
            <dd>{profile.subjectId}</dd>
            <dt>Member since</dt>
            <dd>{new Date(profile.createdAt).toLocaleDateString()}</dd>
          </dl>
        </div>
      ) : (
        !error && <p className="muted">Loading your profile…</p>
      )}
    </>
  );
}
