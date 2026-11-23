"use client";

import { Fragment, useState } from "react";

import {
  addMyTraining,
  deleteMyTraining,
  type TrainingIntensity,
  type TrainingSession,
  type TrainingSport,
} from "@mimos/api-client";

import { apiClient } from "@/lib/api";
import {
  INTENSITIES,
  SPORTS,
  TRAINING_FAILURES,
  TRAINING_LABEL,
  addTrainingLabelFor,
  removeSessionLabel,
  sessionParts,
  trainingLabelFor,
} from "@/lib/fuel";
import { TRAINING_FIELD_ERRORS, namedFields } from "@/lib/fuel-fields";

type SessionDraft = { sport: TrainingSport; minutes: string; intensity: TrainingIntensity };

/**
 * A day's training while its fuel shows: each session, a way to remove one
 * entered by hand, and a form to add one. Every change reloads the week, so
 * the day's target follows at once.
 */
export function TrainingSlot({
  date,
  sessions,
  onChange,
}: {
  date: string;
  sessions: TrainingSession[];
  onChange: () => Promise<void>;
}) {
  const [draft, setDraft] = useState<SessionDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState<"minutes" | keyof typeof TRAINING_FAILURES | null>(null);
  const minutesError = `training-${date}-minutes-error`;

  const edit = (patch: Partial<SessionDraft>) => {
    setDraft((current) => current && { ...current, ...patch });
    setProblem(null);
  };

  const add = async (session: SessionDraft) => {
    const minutes = Number(session.minutes);
    if (session.minutes.trim() === "" || !Number.isInteger(minutes)) {
      setProblem("minutes");
      return;
    }
    setSaving(true);
    const result = await addMyTraining({
      client: apiClient,
      body: { date, sport: session.sport, durationMinutes: minutes, intensity: session.intensity },
    });
    setSaving(false);
    if (result.error) {
      const named = namedFields((result.error as { detail?: string }).detail, TRAINING_FIELD_ERRORS);
      setProblem(named.length > 0 ? "minutes" : "add");
      return;
    }
    setDraft(null);
    await onChange();
  };

  const remove = async (session: TrainingSession) => {
    const result = await deleteMyTraining({ client: apiClient, path: { sessionId: session.id } });
    if (result.error) {
      setProblem("remove");
      return;
    }
    setProblem(null);
    await onChange();
  };

  return (
    // biome-ignore lint/a11y/useSemanticElements: a <fieldset> has the same group role but brings its own border, padding, and form semantics.
    <div className="inspector-section" role="group" aria-label={trainingLabelFor(date)}>
      <span className="label">{TRAINING_LABEL}</span>
      {sessions.length === 0 && !draft && <p className="muted">No training.</p>}
      {sessions.map((session) => (
        <div key={session.id} className="training-session">
          <TrainingParts parts={sessionParts(session)} />
          {session.source === "MANUAL" && (
            <button
              type="button"
              className="button secondary small"
              onClick={() => void remove(session)}
              aria-label={removeSessionLabel(session)}
            >
              ✕
            </button>
          )}
        </div>
      ))}
      {problem !== null && problem !== "minutes" && (
        <p className="error-text" role="alert">
          {TRAINING_FAILURES[problem]}
        </p>
      )}
      {draft ? (
        <form
          className="training-form"
          aria-label={addTrainingLabelFor(date)}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void add(draft);
          }}
        >
          <label>
            Sport
            <select value={draft.sport} onChange={(event) => edit({ sport: event.target.value as TrainingSport })}>
              {Object.entries(SPORTS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <div>
            <label>
              Minutes
              <input
                type="number"
                inputMode="numeric"
                min={1}
                max={1440}
                step={1}
                value={draft.minutes}
                onChange={(event) => edit({ minutes: event.target.value })}
                aria-invalid={problem === "minutes" || undefined}
                aria-describedby={problem === "minutes" ? minutesError : undefined}
              />
            </label>
            {problem === "minutes" && (
              <p className="error-text" id={minutesError}>
                {TRAINING_FIELD_ERRORS.durationMinutes}
              </p>
            )}
          </div>
          <label>
            Intensity
            <select
              value={draft.intensity}
              onChange={(event) => edit({ intensity: event.target.value as TrainingIntensity })}
            >
              {Object.entries(INTENSITIES).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <div className="training-form-actions">
            <button className="button small" type="submit" disabled={saving}>
              {saving ? "Adding…" : "Add"}
            </button>
            <button
              className="button secondary small"
              type="button"
              onClick={() => {
                setDraft(null);
                setProblem(null);
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <button
          type="button"
          className="button secondary small"
          onClick={() => {
            setDraft({ sport: "RIDE", minutes: "", intensity: "ENDURANCE" });
            setProblem(null);
          }}
          aria-label={addTrainingLabelFor(date)}
        >
          + Add training
        </button>
      )}
    </div>
  );
}

/** Each part keeps its words on one line, so a narrow rail wraps between parts only. */
function TrainingParts({ parts }: { parts: string[] }) {
  return (
    <p className="training-parts">
      {parts.map((part, index) => (
        <Fragment key={part}>
          {index > 0 && " "}
          <span>
            {part}
            {index < parts.length - 1 && " ·"}
          </span>
        </Fragment>
      ))}
    </p>
  );
}
