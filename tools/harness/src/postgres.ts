import { readdirSync } from "node:fs";
import { join } from "node:path";

import { compose } from "./compose.ts";
import { database, MIGRATIONS_DIR, REPO_ROOT } from "./config.ts";
import type { ExecResult } from "./exec.ts";

/**
 * The stack's database, through `psql` inside the postgres container — no
 * client on the host, no published credentials (the container's local
 * socket trusts its own user).
 */

/**
 * Every transaction in the session starts read-only unless `write` is set.
 * A guard against accidents, not a security boundary: a query can still
 * `SET default_transaction_read_only = off` itself.
 */
export const READ_ONLY = "-c default_transaction_read_only=on";

export interface PsqlOptions {
  write?: boolean;
  /** CSV output (for parsing); the default is psql's aligned table. */
  csv?: boolean;
}

/** `docker compose exec` arguments for one non-interactive query. */
export function psqlArgs(query: string, options: PsqlOptions = {}): string[] {
  const { user, name } = database();
  return [
    "exec",
    "-T",
    ...(options.write ? [] : ["-e", `PGOPTIONS=${READ_ONLY}`]),
    "postgres",
    // -X: ignore any psqlrc; ON_ERROR_STOP: a failing statement fails the call (exit 1, not 0).
    ...["psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", user, "-d", name],
    ...(options.csv ? ["--csv"] : []),
    "-c",
    query,
  ];
}

export function sql(query: string, options: PsqlOptions = {}): Promise<ExecResult> {
  return compose(psqlArgs(query, options));
}

/** RFC 4180 CSV (psql --csv): a header row, then one record per row. */
export function parseCsv(text: string): Record<string, string>[] {
  const records: string[][] = [];
  let record: string[] = [];
  let value = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i]!;
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        value += '"';
        i++;
      } else if (char === '"') quoted = false;
      else value += char;
    } else if (char === '"') quoted = true;
    else if (char === ",") {
      record.push(value);
      value = "";
    } else if (char === "\n") {
      records.push([...record, value]);
      record = [];
      value = "";
    } else if (char !== "\r") value += char;
  }
  if (value !== "" || record.length > 0) records.push([...record, value]);
  const [header, ...rows] = records;
  return (rows ?? []).map((row) => Object.fromEntries((header ?? []).map((column, index) => [column, row[index] ?? ""])));
}

export const MIGRATION_HISTORY =
  "select installed_rank, version, description, script, success, installed_on from flyway_schema_history order by installed_rank";

export interface MigrationReport {
  applied: number;
  /** Scripts whose row says success = false. */
  failed: string[];
  /** Scripts in the repo with no successful row — the API image predates them, or startup stopped early. */
  pending: string[];
}

/** Compares flyway_schema_history (psql --csv of MIGRATION_HISTORY) with the repo's migration scripts. */
export function migrationReport(history: readonly Record<string, string>[], scripts: readonly string[]): MigrationReport {
  const succeeded = new Set(history.filter((row) => row.success === "t").map((row) => row.script));
  return {
    applied: succeeded.size,
    failed: history.filter((row) => row.success !== "t").map((row) => row.script ?? "?"),
    pending: scripts.filter((script) => !succeeded.has(script)),
  };
}

export function migrationScripts(dir = join(REPO_ROOT, MIGRATIONS_DIR)): string[] {
  return readdirSync(dir)
    .filter((file) => /^V.+__.+\.sql$/.test(file))
    .sort((a, b) => a.localeCompare(b, "en", { numeric: true }));
}
