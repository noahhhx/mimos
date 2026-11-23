"use client";

import Link from "next/link";
import { useState } from "react";

import type { PlanSuggestion } from "@mimos/api-client";

import { dayLabel, formatServings, mealLabel } from "@/lib/format";
import { applySuggestionEntries } from "@/lib/suggestions";

/**
 * Plugins' suggestion cards for the week, each added to the plan in one
 * tap through the plan-entry endpoint (ADR-0006). Shows nothing until a
 * plugin suggests something.
 */
export function Suggestions({
  weekStart,
  suggestions,
  onApplied,
}: {
  weekStart: string;
  suggestions: PlanSuggestion[] | null;
  onApplied: () => Promise<void>;
}) {
  const [applying, setApplying] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const apply = async (suggestion: PlanSuggestion) => {
    setApplying(true);
    const added = await applySuggestionEntries(weekStart, suggestion.entries);
    setApplying(false);
    setNotice(
      added > 0
        ? `Added ${added} meal${added === 1 ? "" : "s"} from "${suggestion.title}".`
        : "Could not add that suggestion.",
    );
    await onApplied();
  };

  if (suggestions === null || suggestions.length === 0) {
    return null;
  }
  return (
    <section className="suggestions" aria-label="Suggestions from plugins">
      <h2>Suggestions</h2>
      <p className="muted">
        From the <Link href="/app/plugins">plugins</Link> you turned on.
      </p>
      {notice && (
        <p className="ok-text" role="status">
          {notice}
        </p>
      )}
      <div className="suggestion-cards">
        {suggestions.map((suggestion) => (
          <article key={`${suggestion.pluginId}:${suggestion.title}`} className="suggestion-card">
            <div className="suggestion-head">
              {suggestion.icon && (
                <span className="suggestion-icon" aria-hidden="true">
                  {suggestion.icon}
                </span>
              )}
              <h3>{suggestion.title}</h3>
            </div>
            {suggestion.blurb && <p>{suggestion.blurb}</p>}
            <ul>
              {suggestion.entries.map((entry) => (
                <li key={`${entry.date}:${entry.mealType}:${entry.recipeId}`}>
                  {entry.recipeTitle} · {dayLabel(entry.date)} {mealLabel(entry.mealType)} ·{" "}
                  {formatServings(entry.servings)}
                </li>
              ))}
            </ul>
            <div className="suggestion-actions">
              <button type="button" className="button" disabled={applying} onClick={() => void apply(suggestion)}>
                {applying ? "Adding…" : "Add to plan"}
              </button>
              <span className="muted">via {suggestion.pluginName}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
