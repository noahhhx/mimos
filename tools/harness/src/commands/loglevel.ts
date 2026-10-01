import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

import { LOG_LEVELS, loggerLevel, setLoggerLevel, type LoggerLevel } from "../actuator.ts";
import { displayCommand, parseCommandArgs, UsageError } from "../args.ts";
import { finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { DEFAULT_USER, LOGLEVEL_STATE, REPO_ROOT } from "../config.ts";
import { appendSummary } from "../run.ts";
import { fetchToken } from "./token.ts";

/**
 * Live log levels through the actuator's loggers endpoint (debug overlay).
 * The first change to a logger remembers its configured level in
 * .harness/loglevels.json, so `--reset` restores exactly what was there —
 * across runs, until the reset.
 */

export type LoglevelArgs =
  | { kind: "show"; logger: string; run: string | undefined }
  | { kind: "set"; logger: string; level: string; run: string | undefined }
  | { kind: "reset"; run: string | undefined };

/** Logger names as Logback takes them: `ROOT`, or dotted Java identifiers. */
const LOGGER = /^(ROOT|[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*)$/;

export function parseLoglevelArgs(argv: string[]): LoglevelArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: { reset: { type: "boolean" }, ...RUN_OPTION },
  });
  if (values.reset) {
    if (positionals.length > 0) throw new UsageError("--reset takes no logger or level");
    return { kind: "reset", run: values.run };
  }
  const [logger, level, ...extra] = positionals;
  if (logger === undefined || extra.length > 0) throw new UsageError("expected <logger> [<level>], or --reset");
  if (!LOGGER.test(logger)) throw new UsageError(`not a logger name: ${logger}`);
  if (level === undefined) return { kind: "show", logger, run: values.run };
  const upper = level.toUpperCase();
  if (!(LOG_LEVELS as readonly string[]).includes(upper)) {
    throw new UsageError(`unknown level ${level} — one of ${LOG_LEVELS.join(", ")}`);
  }
  return { kind: "set", logger, level: upper, run: values.run };
}

/** Logger → the configured level it had before the harness first changed it (null: it inherited). */
export type Originals = Record<string, string | null>;

const STATE_PATH = join(REPO_ROOT, LOGLEVEL_STATE);

function readOriginals(): Originals {
  return existsSync(STATE_PATH) ? (JSON.parse(readFileSync(STATE_PATH, "utf8")) as Originals) : {};
}

function writeOriginals(originals: Originals): void {
  mkdirSync(dirname(STATE_PATH), { recursive: true });
  writeFileSync(STATE_PATH, `${JSON.stringify(originals, null, 2)}\n`);
}

/** Remembers a logger's original level unless an earlier change already did. */
export function remember(originals: Originals, logger: string, before: LoggerLevel): Originals {
  return logger in originals ? originals : { ...originals, [logger]: before.configuredLevel };
}

function describe(level: LoggerLevel): string {
  return `${level.configuredLevel ?? "inherited"} (effective ${level.effectiveLevel})`;
}

export const loglevelCommand: Command = {
  name: "loglevel",
  summary: "Show or change an API logger's level live, or reset the ones changed",
  usage: `harness loglevel <logger> [<level>]
harness loglevel --reset

  <logger>   ROOT or a logger name, e.g. org.springframework.web
  <level>    ${LOG_LEVELS.join(" | ")} (case-insensitive); without it, prints the current level
  --reset    restore every logger the harness changed to its original level
${RUN_USAGE}

Needs the debug overlay (harness up --debug). Takes effect immediately, no restart;
a restart of the API also reverts every change. Originals are kept in
${LOGLEVEL_STATE} until --reset. Changes are recorded in summary.md.`,
  async run(argv) {
    const args = parseLoglevelArgs(argv);
    const startedAt = new Date();
    const token = await fetchToken(DEFAULT_USER);

    if (args.kind === "show") {
      process.stdout.write(`${args.logger}: ${describe(await loggerLevel(token, args.logger))}\n`);
      return 0;
    }

    const run = startRun("loglevel", startedAt, args.run);
    const lines: string[] = [];
    if (args.kind === "set") {
      const before = await loggerLevel(token, args.logger);
      writeOriginals(remember(readOriginals(), args.logger, before));
      await setLoggerLevel(token, args.logger, args.level);
      const after = await loggerLevel(token, args.logger);
      lines.push(`${args.logger}: ${describe(before)} → ${describe(after)}`);
    } else {
      const originals = readOriginals();
      for (const [logger, level] of Object.entries(originals)) {
        const before = await loggerLevel(token, logger);
        await setLoggerLevel(token, logger, level);
        const after = await loggerLevel(token, logger);
        lines.push(`${logger}: ${describe(before)} → ${describe(after)}`);
      }
      rmSync(STATE_PATH, { force: true });
      if (lines.length === 0) lines.push("nothing to reset — no logger was changed since the last reset");
    }

    appendSummary(run, [`## \`${displayCommand(["loglevel", ...argv])}\``, "", ...lines.map((line) => `- ${line}`)].join("\n"));
    await finishRun(run, ["loglevel", ...argv], startedAt, 0);
    process.stdout.write(`${lines.join("\n")}\n`);
    return 0;
  },
};
