import { existsSync } from "node:fs";
import { join } from "node:path";

import { displayCommand, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { composeInteractive } from "../compose.ts";
import { database } from "../config.ts";
import { sql } from "../postgres.ts";
import { redactText } from "../redact.ts";
import { appendSummary, writeRunFile, type Run } from "../run.ts";

/** Database access: `sql` records one query's output in the run (agents); `psql` is a terminal session (humans). */

export interface SqlArgs {
  query: string;
  write: boolean;
  csv: boolean;
  run: string | undefined;
}

export function parseSqlArgs(argv: string[]): SqlArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: { write: { type: "boolean" }, csv: { type: "boolean" }, ...RUN_OPTION },
  });
  const [query, ...extra] = positionals;
  if (query === undefined || query.trim() === "" || extra.length > 0) {
    throw new UsageError('expected one query, quoted: harness sql "select …"');
  }
  return { query, write: values.write ?? false, csv: values.csv ?? false, run: values.run };
}

/** `sql/1.txt`, `sql/2.csv`, … — numbered across the run, whatever the format. */
function outputFile(run: Run, csv: boolean): string {
  let n = 1;
  while (existsSync(join(run.dir, `sql/${n}.txt`)) || existsSync(join(run.dir, `sql/${n}.csv`))) n++;
  return `sql/${n}.${csv ? "csv" : "txt"}`;
}

export const sqlCommand: Command = {
  name: "sql",
  summary: "Run one SQL query against the stack's database and record the output",
  usage: `harness sql "<query>" [--write] [--csv]

  --write   allow writes (by default every transaction is read-only)
  --csv     CSV output instead of psql's aligned table
${RUN_USAGE}

Runs psql inside the postgres container as ${database().user} on ${database().name}. Writes the
output to sql/<n>.txt (or .csv) and the query and output to summary.md, and prints
the output. Read-only is a guard against accidents (default_transaction_read_only),
not a security boundary. Exit code: 0 when the query succeeded, 1 otherwise.`,
  async run(argv) {
    const args = parseSqlArgs(argv);
    const startedAt = new Date();
    const run = startRun("sql", startedAt, args.run);
    const result = await sql(args.query, { write: args.write, csv: args.csv });
    const output = redactText(result.stdout);
    const errors = redactText(result.stderr).trim();
    const file = outputFile(run, args.csv);
    writeRunFile(run, file, output);

    const exitCode = result.code === 0 ? 0 : 1;
    const lines = output.trimEnd().split("\n");
    const section = [
      `## \`${displayCommand(["sql", ...argv])}\` — ${exitCode === 0 ? "ok" : "failed"}`,
      "",
      `- **Mode:** ${args.write ? "read-write (`--write`)" : "read-only"} · psql exited ${result.code}`,
      `- **Output:** \`${file}\``,
      "",
      fenced(args.query, "sql"),
    ];
    if (output.trim() !== "") {
      section.push("", fenced(lines.slice(0, 60).join("\n") + (lines.length > 60 ? `\n… ${lines.length - 60} more lines in ${file}` : "")));
    }
    if (errors !== "") section.push("", "psql reported:", "", fenced(errors));
    appendSummary(run, section.join("\n"));
    await finishRun(run, ["sql", ...argv], startedAt, exitCode);

    process.stdout.write(output);
    if (errors !== "") process.stderr.write(`${errors}\n`);
    return exitCode;
  },
};

export const psqlCommand: Command = {
  name: "psql",
  summary: "Open an interactive psql session on the stack's database",
  usage: `harness psql

Runs psql inside the postgres container as ${database().user} on ${database().name}, on this
terminal. Nothing is recorded and writes are allowed — agents use harness sql.`,
  async run(argv) {
    parseCommandArgs({ args: argv, options: {} });
    const { user, name } = database();
    return composeInteractive(["exec", "postgres", "psql", "-U", user, "-d", name]);
  },
};
