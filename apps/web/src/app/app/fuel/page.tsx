"use client";

import { useEffect, useState } from "react";

import {
  getMyFuelProfile,
  getMyIntervalsIcu,
  updateMyFuelProfile,
  type FuelGoal,
  type FuelProfile,
  type FuelProfileInput,
  type FuelSex,
} from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { IntervalsIcuSection } from "@/components/intervals-icu-section";
import { PageHeader } from "@/components/page-header";
import { SignInPrompt } from "@/components/sign-in-prompt";
import { apiClient } from "@/lib/api";
import { FIELD_ERRORS, namedFields, type FuelField } from "@/lib/fuel-fields";
import {
  INTERVALS_ICU_FAILURES,
  bodyFatNote,
  hintSuggestions,
  type ConnectionLoad,
  type HintField,
} from "@/lib/intervals-icu";

type Load<T> = { state: "loading" } | { state: "error" } | { state: "ok"; data: T };

const GOALS: { value: FuelGoal; label: string; proteinGPerKg: number }[] = [
  { value: "FUEL", label: "Fuel my training", proteinGPerKg: 1.6 },
  { value: "GRADUAL_LOSS", label: "Lose weight gradually", proteinGPerKg: 2.0 },
  { value: "FASTER_LOSS", label: "Lose weight faster", proteinGPerKg: 2.2 },
  { value: "GAIN", label: "Gain weight", proteinGPerKg: 1.8 },
];

const SEXES: { value: FuelSex; label: string }[] = [
  { value: "FEMALE", label: "Female" },
  { value: "MALE", label: "Male" },
  { value: "UNSPECIFIED", label: "Prefer not to say" },
];

type Values = {
  enabled: boolean;
  weightKg: string;
  sex: FuelSex;
  bodyFatPercent: string;
  ftpWatts: string;
  goal: FuelGoal;
  proteinGPerKg: string;
};

const text = (value: number | undefined) => (value === undefined ? "" : String(value));
const number = (value: string) => (value.trim() === "" ? undefined : Number(value));

function toValues(profile: FuelProfile): Values {
  return {
    enabled: profile.enabled,
    weightKg: text(profile.weightKg),
    sex: profile.sex,
    bodyFatPercent: text(profile.bodyFatPercent),
    ftpWatts: text(profile.ftpWatts),
    goal: profile.goal,
    proteinGPerKg: text(profile.proteinGPerKg),
  };
}

export default function FuelPage() {
  const { user } = useAuth();
  const [values, setValues] = useState<Load<Values>>({ state: "loading" });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FuelField[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [connection, setConnection] = useState<ConnectionLoad>({ state: "loading" });

  const subject = user?.profile.sub ?? null;
  useEffect(() => {
    if (!subject) {
      return;
    }
    let cancelled = false;
    void getMyFuelProfile({ client: apiClient }).then((result) => {
      if (!cancelled) {
        setValues(result.data ? { state: "ok", data: toValues(result.data) } : { state: "error" });
      }
    });
    void getMyIntervalsIcu({ client: apiClient }).then((result) => {
      if (cancelled) {
        return;
      }
      if (result.data) {
        setConnection({ state: "ok", data: result.data });
      } else {
        // A 404 means this instance does not offer intervals.icu; training is entered by hand.
        setConnection(result.response?.status === 404 ? { state: "off" } : { state: "error" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [subject]);

  if (!user) {
    return (
      <>
        <PageHeader title="Fuel" />
        <SignInPrompt>You need to sign in to use Mimos.</SignInPrompt>
      </>
    );
  }

  if (values.state !== "ok") {
    return (
      <>
        <PageHeader eyebrow="Account" title="Fuel" />
        {values.state === "loading" ? (
          <p className="muted">Loading your fuel settings…</p>
        ) : (
          <p className="card error" role="alert">
            Could not load your fuel settings.
          </p>
        )}
      </>
    );
  }

  const form = values.data;
  const hints = connection.state === "ok" ? connection.data.hints : undefined;
  const suggestions = hintSuggestions(form, hints);
  const set = (patch: Partial<Values>) => {
    setValues({ state: "ok", data: { ...form, ...patch } });
    setSaved(false);
  };

  const save = async () => {
    setError(null);
    setSaved(false);
    const weightKg = number(form.weightKg);
    const ftpWatts = number(form.ftpWatts);
    const local: FuelField[] = [];
    if (weightKg === undefined) {
      local.push("weightKg");
    }
    if (ftpWatts !== undefined && !Number.isInteger(ftpWatts)) {
      local.push("ftpWatts");
    }
    setFieldErrors(local);
    if (weightKg === undefined || local.length > 0) {
      return;
    }
    const body: FuelProfileInput = {
      enabled: form.enabled,
      weightKg,
      sex: form.sex,
      bodyFatPercent: number(form.bodyFatPercent),
      ftpWatts,
      goal: form.goal,
      proteinGPerKg: number(form.proteinGPerKg),
    };
    setSaving(true);
    const result = await updateMyFuelProfile({ client: apiClient, body });
    setSaving(false);
    if (result.data) {
      setValues({ state: "ok", data: toValues(result.data) });
      setSaved(true);
      return;
    }
    const named = namedFields((result.error as { detail?: string } | undefined)?.detail, FIELD_ERRORS);
    setFieldErrors(named);
    if (named.length === 0) {
      setError("Could not save your fuel settings.");
    }
  };

  const suggestion = (field: HintField) => {
    const found = suggestions.find((s) => s.field === field);
    return (
      found && (
        <p className="muted suggestion">
          {found.text}{" "}
          <button
            type="button"
            className="button secondary small"
            onClick={() => set({ [field]: found.value })}
            aria-label={found.action}
          >
            Use it
          </button>
        </p>
      )
    );
  };

  const numberField = (field: FuelField, label: string, step: string, hint?: string) => {
    const invalid = fieldErrors.includes(field);
    return (
      <div>
        <label>
          {label}
          <input
            type="number"
            inputMode="decimal"
            step={step}
            value={form[field]}
            onChange={(e) => set({ [field]: e.target.value })}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? `${field}-error` : undefined}
          />
        </label>
        {hint && !invalid && <p className="muted">{hint}</p>}
        {(field === "weightKg" || field === "ftpWatts") && suggestion(field)}
        {invalid && (
          <p className="error-text" id={`${field}-error`}>
            {FIELD_ERRORS[field]}
          </p>
        )}
      </div>
    );
  };

  return (
    <>
      <PageHeader eyebrow="Account" title="Fuel" />

      <section className="card" aria-labelledby="about-heading">
        <h2 id="about-heading">Fuel for the day</h2>
        <p>
          Turn this on to see a calorie, carb, protein, and fat target for each day beside what you planned and logged.
          The targets rise with your training and come from your weight, your goal, and the training you add or bring
          in from intervals.icu.
        </p>
        <p>They are yours alone: everyone in a household has their own settings, and plugins never see them.</p>
      </section>

      <form
        className="card form"
        aria-label="Fuel profile"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        {error && (
          <p className="card error" role="alert">
            {error}
          </p>
        )}
        <div className="choices">
          <label>
            <input type="checkbox" checked={form.enabled} onChange={(e) => set({ enabled: e.target.checked })} />
            Show fuel targets
          </label>
        </div>

        <h2>About you</h2>
        {numberField("weightKg", "Weight (kg)", "0.1")}
        <div className="choices" role="radiogroup" aria-label="Sex">
          {SEXES.map((sex) => (
            <label key={sex.value}>
              <input type="radio" name="sex" checked={form.sex === sex.value} onChange={() => set({ sex: sex.value })} />
              {sex.label}
            </label>
          ))}
        </div>
        {suggestion("sex")}
        {numberField(
          "bodyFatPercent",
          "Body fat (%)",
          "0.1",
          bodyFatNote(form.bodyFatPercent, hints) ??
            "Optional. Without it, Mimos assumes 15% for men, 25% for women, and 20% otherwise.",
        )}
        {numberField("ftpWatts", "FTP (watts)", "1", "Optional. Your functional threshold power on the bike.")}

        <h2>Your goal</h2>
        <div className="choices" role="radiogroup" aria-label="Goal">
          {GOALS.map((goal) => (
            <label key={goal.value}>
              <input
                type="radio"
                name="goal"
                checked={form.goal === goal.value}
                onChange={() => set({ goal: goal.value, proteinGPerKg: String(goal.proteinGPerKg) })}
              />
              {goal.label}
            </label>
          ))}
        </div>
        {numberField("proteinGPerKg", "Protein (g per kg)", "0.1", "Set by your goal. Change it if you like.")}

        <p className="actions">
          <button type="submit" className="button" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
          {saved && (
            <span className="muted" role="status">
              Saved.
            </span>
          )}
        </p>
      </form>

      {connection.state === "ok" && (
        <IntervalsIcuSection
          connection={connection.data}
          onChange={(data) => setConnection({ state: "ok", data })}
        />
      )}
      {connection.state === "error" && (
        <p className="card error" role="alert">
          {INTERVALS_ICU_FAILURES.load}
        </p>
      )}
    </>
  );
}
