import { DatabaseSync } from "node:sqlite";

import { countryByCode } from "./countries.ts";
import type { Effect, Ledger } from "./wheel.ts";

/**
 * Each household's wheel, kept in SQLite under the pseudonymous `subject`
 * Mimos sends (ADR-0017, ADR-0019). Calls are synchronous, so one request's read and write
 * never interleave with another's.
 */
export type Store = {
  ledger(subject: string): Ledger;
  apply(subject: string, week: string, effect: Effect): void;
  close(): void;
};

const SCHEMA = `
CREATE TABLE IF NOT EXISTS choices (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  subject TEXT NOT NULL,
  week_start TEXT NOT NULL,
  country TEXT NOT NULL,
  UNIQUE (subject, week_start),
  UNIQUE (subject, country)
);
CREATE TABLE IF NOT EXISTS removed (
  subject TEXT NOT NULL,
  country TEXT NOT NULL,
  PRIMARY KEY (subject, country)
);
`;

/** Opens (and creates, if needed) the database at `path`; `:memory:` for tests. */
export function openStore(path: string): Store {
  const db = new DatabaseSync(path);
  db.exec(SCHEMA);
  const selectChoices = db.prepare("SELECT week_start, country FROM choices WHERE subject = ? ORDER BY seq");
  const selectRemoved = db.prepare("SELECT country FROM removed WHERE subject = ?");
  const insertChoice = db.prepare(
    "INSERT INTO choices (subject, week_start, country) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
  );
  const deleteChoice = db.prepare("DELETE FROM choices WHERE subject = ? AND week_start = ?");
  const insertRemoved = db.prepare("INSERT INTO removed (subject, country) VALUES (?, ?) ON CONFLICT DO NOTHING");
  const deleteRemoved = db.prepare("DELETE FROM removed WHERE subject = ?");

  return {
    ledger(subject) {
      const choices = selectChoices.all(subject).flatMap((row) => {
        const country = countryByCode(String(row.country));
        return country === undefined ? [] : [{ week: String(row.week_start), country }];
      });
      const removed = new Set(selectRemoved.all(subject).map((row) => String(row.country)));
      return { choices, removed };
    },
    apply(subject, week, effect) {
      switch (effect.kind) {
        case "choose":
          insertChoice.run(subject, week, effect.country.code);
          return;
        case "unchoose":
          deleteChoice.run(subject, week);
          return;
        case "remove":
          insertRemoved.run(subject, effect.country.code);
          return;
        case "restore":
          deleteRemoved.run(subject);
          return;
      }
    },
    close() {
      db.close();
    },
  };
}
