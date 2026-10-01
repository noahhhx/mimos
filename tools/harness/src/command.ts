import { join } from "node:path";

import { containerImages, gitState } from "./compose.ts";
import { REPO_ROOT, RUNS_DIR } from "./config.ts";
import { openRun, recordInvocation, type Run } from "./run.ts";

export interface Command {
  name: string;
  summary: string;
  usage: string;
  /** Resolves to the process exit code. */
  run(argv: string[]): Promise<number>;
}

export const RUN_OPTION = {
  run: { type: "string" },
} as const;

export const RUN_USAGE = "  --run <name|path|latest>  append to an existing run instead of starting a new one";

export function startRun(label: string, now: Date, reuse: string | undefined): Run {
  const run = openRun(join(REPO_ROOT, RUNS_DIR), label, now, reuse);
  process.stderr.write(`run: ${run.dir}${run.created ? "" : " (appending)"}\n`);
  return run;
}

/** Records the invocation in command.json: argv, timing, git state, and the images the stack runs. */
export async function finishRun(run: Run, argv: string[], startedAt: Date, exitCode: number): Promise<void> {
  const [git, images] = await Promise.all([gitState(), containerImages().catch(() => [])]);
  recordInvocation(run, {
    argv,
    startedAt: startedAt.toISOString(),
    finishedAt: new Date().toISOString(),
    exitCode,
    git,
    images,
  });
}

/** A fenced code block for summary.md (a tilde fence if the text contains a backtick one). */
export function fenced(text: string, language = ""): string {
  const fence = text.includes("```") ? "~~~~" : "```";
  return `${fence}${language}\n${text.trimEnd()}\n${fence}`;
}
