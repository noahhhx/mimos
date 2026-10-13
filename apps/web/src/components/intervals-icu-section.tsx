"use client";

import { useState } from "react";

import {
  connectMyIntervalsIcu,
  disconnectMyIntervalsIcu,
  syncMyIntervalsIcu,
  type IntervalsIcuConnection,
} from "@mimos/api-client";

import { apiClient } from "@/lib/api";
import { INTERVALS_ICU_FAILURES, connectionSummary, syncNote } from "@/lib/intervals-icu";

const time = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });

const detailOf = (error: unknown) => (error as { detail?: string } | undefined)?.detail;

/**
 * The Fuel page's intervals.icu connection (ADR-0022): connect with an
 * athlete id and API key, see when training last synced, sync now, or
 * disconnect. A broken connection asks for the key again.
 */
export function IntervalsIcuSection({
  connection,
  onChange,
}: {
  connection: IntervalsIcuConnection;
  onChange: (connection: IntervalsIcuConnection) => void;
}) {
  const [athleteId, setAthleteId] = useState(connection.athleteId ?? "");
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setProblem(null);
    try {
      await action();
    } finally {
      setBusy(false);
    }
  };

  const connect = () =>
    run(async () => {
      const result = await connectMyIntervalsIcu({ client: apiClient, body: { athleteId, apiKey } });
      if (!result.data) {
        setProblem(detailOf(result.error) ?? INTERVALS_ICU_FAILURES.connect);
        return;
      }
      setApiKey("");
      onChange(result.data);
      const synced = await syncMyIntervalsIcu({ client: apiClient });
      if (synced.data) {
        onChange(synced.data);
      }
    });

  const sync = () =>
    run(async () => {
      const result = await syncMyIntervalsIcu({ client: apiClient });
      if (result.data) {
        onChange(result.data);
      } else {
        setProblem(INTERVALS_ICU_FAILURES.sync);
      }
    });

  const disconnect = () =>
    run(async () => {
      const result = await disconnectMyIntervalsIcu({ client: apiClient });
      setConfirming(false);
      if (result.error) {
        setProblem(INTERVALS_ICU_FAILURES.disconnect);
        return;
      }
      setAthleteId("");
      onChange({ status: "DISCONNECTED" });
    });

  const disconnectControls = confirming ? (
    <>
      <span>Disconnect? Training from intervals.icu goes; training you added stays.</span>
      <button type="button" className="button secondary" onClick={() => void disconnect()} disabled={busy}>
        Yes, disconnect
      </button>
      <button type="button" className="button secondary" onClick={() => setConfirming(false)} disabled={busy}>
        Cancel
      </button>
    </>
  ) : (
    <button type="button" className="button secondary" onClick={() => setConfirming(true)} disabled={busy}>
      Disconnect
    </button>
  );

  const note = syncNote(connection, time);
  const connected = connection.status === "CONNECTED";
  const broken = connection.status === "BROKEN";

  return (
    <section className="card form" aria-labelledby="intervals-icu-heading">
      <h2 id="intervals-icu-heading">intervals.icu</h2>
      <p>
        Connect intervals.icu to bring in your planned workouts and completed activities, so each day&apos;s fuel
        follows your training. Mimos checks for changes on its own, and Refresh checks now.
      </p>
      {broken ? (
        <p className="error-text" role="alert">
          {connectionSummary(connection, time)}
        </p>
      ) : (
        <p role="status">{connectionSummary(connection, time)}</p>
      )}
      {note && <p className="muted">{note}</p>}
      {problem && (
        <p className="error-text" role="alert">
          {problem}
        </p>
      )}

      {!connected && (
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            void connect();
          }}
        >
          <label>
            Athlete ID
            <input
              value={athleteId}
              onChange={(e) => setAthleteId(e.target.value)}
              placeholder="i12345"
              autoComplete="off"
              spellCheck={false}
            />
          </label>
          <label>
            API key
            <input
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              autoComplete="off"
              spellCheck={false}
            />
          </label>
          <p className="muted">
            Both are in intervals.icu under Settings, in the Developer Settings section. Mimos keeps the key encrypted
            and never shows it again.
          </p>
          <p className="actions">
            <button type="submit" className="button" disabled={busy || !athleteId.trim() || !apiKey.trim()}>
              {busy ? "Connecting…" : "Connect"}
            </button>
            {broken && disconnectControls}
          </p>
        </form>
      )}

      {connected && (
        <p className="actions">
          <button type="button" className="button" onClick={() => void sync()} disabled={busy}>
            {busy ? "Syncing…" : "Refresh"}
          </button>
          {disconnectControls}
        </p>
      )}
    </section>
  );
}
