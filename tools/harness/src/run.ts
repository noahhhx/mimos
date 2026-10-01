import {
  appendFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  renameSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { basename, dirname, isAbsolute, join, resolve } from "node:path";

import { HarnessError } from "./args.ts";

/**
 * A run folder: `.harness/runs/<UTC timestamp>-<label>/`, holding everything
 * one investigation produced, with `summary.md` as the entry point and
 * `latest` pointing at the most recently used run. Callers redact before
 * writing (see redact.ts).
 */

export interface Run {
  /** Absolute path of the run folder. */
  dir: string;
  name: string;
  /** False when `--run` appended to an existing run. */
  created: boolean;
}

/** One entry per harness invocation in the run, in order. */
export interface Invocation {
  argv: string[];
  startedAt: string;
  finishedAt: string;
  exitCode: number;
  git: { sha: string | null; dirty: boolean | null };
  images: { service: string; image: string; id: string }[];
}

/** `2026-10-01T15-04-12Z-create-recipe` — sortable, filesystem-safe on every OS. */
export function runName(now: Date, label: string): string {
  const stamp = now.toISOString().replace(/\.\d{3}Z$/, "Z").replaceAll(":", "-");
  const slug = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug ? `${stamp}-${slug}` : stamp;
}

/**
 * Opens a run: a new folder, or with `reuse` (a run name, a path, or `latest`)
 * an existing one so a multi-command investigation stays together.
 */
export function openRun(runsDir: string, label: string, now: Date, reuse?: string): Run {
  let run: Run;
  if (reuse !== undefined) {
    const dir = isAbsolute(reuse) || reuse.includes("/") ? resolve(reuse) : join(runsDir, reuse);
    if (!existsSync(join(dir, "summary.md"))) {
      throw new HarnessError(`--run ${reuse}: no run folder at ${dir}`);
    }
    // `--run latest` resolves through the link so the run keeps its real name.
    const real = realpathSync(dir);
    run = { dir: real, name: basename(real), created: false };
  } else {
    mkdirSync(runsDir, { recursive: true });
    const base = runName(now, label);
    let name = base;
    for (let n = 2; existsSync(join(runsDir, name)); n++) {
      name = `${base}-${n}`;
    }
    const dir = join(runsDir, name);
    mkdirSync(dir);
    writeFileSync(
      join(dir, "summary.md"),
      `# Run ${name}\n\nStarted ${now.toISOString()}. One section per harness command, oldest first.\n`,
    );
    run = { dir, name, created: true };
  }
  linkLatest(runsDir, run.name);
  return run;
}

/** Points `latest` at `name` atomically (a relative link, so the folder can be moved or archived). */
export function linkLatest(runsDir: string, name: string): void {
  const temp = join(runsDir, `.latest-${process.pid}`);
  symlinkSync(name, temp);
  renameSync(temp, join(runsDir, "latest"));
}

export function writeRunFile(run: Run, file: string, content: string): string {
  const path = join(run.dir, file);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
  return path;
}

/** Appends one JSON line; returns its 1-based line number for cross-references. */
export function appendJsonLine(run: Run, file: string, value: unknown): number {
  const path = join(run.dir, file);
  mkdirSync(dirname(path), { recursive: true });
  const existing = existsSync(path) ? readFileSync(path, "utf8").split("\n").filter(Boolean).length : 0;
  appendFileSync(path, `${JSON.stringify(value)}\n`);
  return existing + 1;
}

export function appendSummary(run: Run, markdown: string): void {
  appendFileSync(join(run.dir, "summary.md"), `\n${markdown.trimEnd()}\n`);
}

export function readInvocations(run: Run): Invocation[] {
  const path = join(run.dir, "command.json");
  return existsSync(path) ? (JSON.parse(readFileSync(path, "utf8")) as Invocation[]) : [];
}

export function recordInvocation(run: Run, invocation: Invocation): void {
  writeRunFile(run, "command.json", `${JSON.stringify([...readInvocations(run), invocation], null, 2)}\n`);
}

/** When the run began — the start of its log window. */
export function runStartedAt(run: Run, fallback: Date): Date {
  const first = readInvocations(run)[0];
  return first ? new Date(first.startedAt) : fallback;
}
