"use client";

import type { FuelDay, MealPlan, Person } from "@mimos/api-client";

import { chipLabels, eatsIt } from "@/lib/diners";
import { MEAL_TYPES, dayLabel, mealLabel } from "@/lib/format";
import { TRAINING_LABEL, trainingLabelFor } from "@/lib/fuel";
import { dayHead, ledgerFuel, ledgerFuelLabel, sessionShort, type LedgerSelection } from "@/lib/plan-ledger";
import { amountText, entryName } from "@/lib/planned-item";

type Entry = MealPlan["entries"][number];

/**
 * The week as a table: days across, meals down, and cells that show only
 * what is planned. While fuel shows, a day's head and its Training and
 * Planned cells select the day; a planned meal selects itself, and each
 * cell's + opens the picker for that slot.
 */
export function Ledger({
  weekStart,
  days,
  today,
  entries,
  members,
  shared,
  fuelByDay,
  targetsOn,
  selectedEntryId,
  selectedDay,
  onChoose,
  onAdd,
}: {
  weekStart: string;
  days: string[];
  today: string;
  entries: Entry[];
  members: Person[];
  shared: boolean;
  fuelByDay: Map<string, FuelDay>;
  targetsOn: boolean;
  selectedEntryId: string | undefined;
  selectedDay: string | null;
  onChoose: (next: Exclude<LedgerSelection, null>, from: HTMLElement) => void;
  onAdd: (date: string, mealType: (typeof MEAL_TYPES)[number]) => void;
}) {
  const memberLabels = chipLabels(members.map((member) => member.displayName));
  const dinerLabels = (entry: Entry) =>
    members.flatMap((member, index) =>
      entry.diners.some((diner) => diner.id === member.id)
        ? [{ id: member.id, label: memberLabels[index], name: member.displayName }]
        : [],
    );

  return (
    <div className="ledger-scroll">
      <table className="ledger" aria-label={`Meals for the week of ${dayLabel(weekStart)}`}>
        <colgroup>
          <col className="ledger-rowhead" />
          {days.map((day) => (
            <col key={day} />
          ))}
        </colgroup>
        <thead>
          <tr>
            <td />
            {days.map((day) => {
              const head = dayHead(day);
              const content = (
                <>
                  <span className="ledger-weekday">{day === today ? "Today" : head.weekday}</span>
                  <span className="ledger-date">{head.date}</span>
                </>
              );
              return (
                <th key={day} scope="col" className={day === today ? "today" : undefined}>
                  {fuelByDay.size > 0 ? (
                    <button
                      type="button"
                      className="ledger-day"
                      aria-label={day === today ? `${dayLabel(day)}, today` : dayLabel(day)}
                      aria-pressed={selectedDay === day}
                      onClick={(event) => onChoose({ kind: "day", date: day }, event.currentTarget)}
                    >
                      {content}
                    </button>
                  ) : (
                    <span className="ledger-day" aria-label={dayLabel(day)}>
                      {content}
                    </span>
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {targetsOn && (
            <tr className="ledger-meta-row">
              <th scope="row">{TRAINING_LABEL}</th>
              {days.map((day) => {
                const sessions = fuelByDay.get(day)?.sessions ?? [];
                return (
                  <td key={day}>
                    <button
                      type="button"
                      className="ledger-meta"
                      aria-label={`${trainingLabelFor(day)}: ${sessions.length > 0 ? sessions.map(sessionShort).join(", ") : "none"}`}
                      onClick={(event) => onChoose({ kind: "day", date: day }, event.currentTarget)}
                    >
                      {sessions.length > 0 ? (
                        sessions.map((session) => <span key={session.id}>{sessionShort(session)}</span>)
                      ) : (
                        <span className="muted">Rest</span>
                      )}
                    </button>
                  </td>
                );
              })}
            </tr>
          )}
          {fuelByDay.size > 0 && (
            <tr className="ledger-meta-row">
              <th scope="row">Planned</th>
              {days.map((day) => {
                const figures = ledgerFuel(fuelByDay.get(day)?.target, fuelByDay.get(day)?.planned);
                return (
                  <td key={day}>
                    <button
                      type="button"
                      className="ledger-meta"
                      aria-label={ledgerFuelLabel(day, figures)}
                      onClick={(event) => onChoose({ kind: "day", date: day }, event.currentTarget)}
                    >
                      <span className="ledger-figure">{figures.value}</span>
                      {figures.detail && <span className="muted">{figures.detail}</span>}
                    </button>
                  </td>
                );
              })}
            </tr>
          )}
          {MEAL_TYPES.map((mealType) => (
            <tr key={mealType}>
              <th scope="row">{mealLabel(mealType)}</th>
              {days.map((day) => {
                const slot = entries.filter((entry) => entry.date === day && entry.mealType === mealType);
                return (
                  <td key={day} className={day === today ? "today" : undefined}>
                    <div className={slot.length === 0 ? "ledger-cell empty" : "ledger-cell"}>
                      {slot.map((entry) => (
                        <button
                          type="button"
                          key={entry.id}
                          className={eatsIt(entry) ? "ledger-entry" : "ledger-entry others"}
                          aria-pressed={selectedEntryId === entry.id}
                          onClick={(event) => onChoose({ kind: "entry", id: entry.id }, event.currentTarget)}
                        >
                          <span className="ledger-entry-name">{entryName(entry)}</span>
                          <span className="ledger-entry-meta">
                            {amountText(entry)}
                            {shared && (
                              <span className="ledger-diners">
                                {dinerLabels(entry).map((diner) => (
                                  <span key={diner.id} className="ledger-diner" title={diner.name}>
                                    {diner.label}
                                  </span>
                                ))}
                              </span>
                            )}
                          </span>
                        </button>
                      ))}
                      <button
                        type="button"
                        className="ledger-add"
                        onClick={() => onAdd(day, mealType)}
                        aria-label={`Add ${mealLabel(mealType)} for ${dayLabel(day)}`}
                      >
                        +
                      </button>
                    </div>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
