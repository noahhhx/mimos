import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";

import { displayCommand, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { debugPorts, endpoints, REPO_ROOT } from "../config.ts";
import { send } from "../http.ts";
import {
  endsWithPrompt,
  hitThreadId,
  jdbLocation,
  JdbSession,
  parseEvaluation,
  parseHits,
  parseLocals,
  parseLocation,
  parseStopResult,
  parseThreads,
  parseWhere,
  resolveLocation,
  sourceRoots,
  stopCommand,
  type BreakLocation,
  type Evaluation,
  type Frame,
  type HitEvent,
  type Locals,
} from "../jdb.ts";
import { redactText } from "../redact.ts";
import { appendSummary, writeRunFile, type Run } from "../run.ts";

/**
 * `harness debug break`: a breakpoint in the API's JVM through jdb (JDWP from
 * the debug overlay), optionally triggered by another harness command. Each
 * hit's stack, locals, `this`, and requested expressions land in
 * debug/<n>/hits.md; the hitting thread alone is suspended, and the session
 * always clears the breakpoint and detaches — a JVM left suspended would look
 * like a hung API.
 */

/** Harness commands that make a request the breakpoint can catch; both accept `--run`. */
const TRIGGERS = ["api", "ui"];

export interface BreakArgs {
  location: BreakLocation;
  /** A harness command line, split into argv (without `--run`, which the debug run supplies). */
  then: string[] | undefined;
  prints: string[];
  hits: number;
  timeoutSeconds: number;
  run: string | undefined;
}

/** Splits a command line the way a POSIX shell would for plain words, quotes, and backslashes (no expansion). */
export function splitWords(line: string): string[] {
  const words: string[] = [];
  let word = "";
  let inWord = false;
  let quote: "'" | '"' | undefined;
  for (let i = 0; i < line.length; i++) {
    const char = line[i] ?? "";
    if (quote === "'") {
      if (char === "'") quote = undefined;
      else word += char;
    } else if (quote === '"') {
      if (char === '"') quote = undefined;
      else if (char === "\\" && /["\\$`]/.test(line[i + 1] ?? "")) word += line[++i];
      else word += char;
    } else if (/\s/.test(char)) {
      if (inWord) words.push(word);
      word = "";
      inWord = false;
    } else {
      inWord = true;
      if (char === "'" || char === '"') quote = char;
      else if (char === "\\" && i + 1 < line.length) word += line[++i];
      else word += char;
    }
  }
  if (quote !== undefined) throw new UsageError(`unterminated ${quote} in: ${line}`);
  if (inWord) words.push(word);
  return words;
}

function positiveInteger(raw: string | undefined, option: string, fallback: number): number {
  if (raw === undefined) return fallback;
  if (!/^\d+$/.test(raw) || Number(raw) < 1) throw new UsageError(`${option} expects a positive whole number, got: ${raw}`);
  return Number(raw);
}

export function parseBreakArgs(argv: string[], roots: readonly string[] = sourceRoots(REPO_ROOT)): BreakArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: {
      then: { type: "string" },
      print: { type: "string", multiple: true },
      hits: { type: "string" },
      timeout: { type: "string" },
      ...RUN_OPTION,
    },
  });
  const [sub, location, ...extra] = positionals;
  if (sub !== "break" || location === undefined || extra.length > 0) {
    throw new UsageError("expected: harness debug break <Class:line | Class.method>");
  }
  let then: string[] | undefined;
  if (values.then !== undefined) {
    then = splitWords(values.then);
    if (then[0] === "harness") then = then.slice(1);
    const [command] = then;
    if (command === undefined || !TRIGGERS.includes(command)) {
      throw new UsageError(`--then runs a harness command that sends a request: ${TRIGGERS.join(" | ")}, e.g. --then "api GET /api/v1/me"`);
    }
    if (then.some((arg) => arg === "--run" || arg.startsWith("--run="))) {
      throw new UsageError("--then must not pass --run: the trigger records into the debug run");
    }
  }
  const prints = values.print ?? [];
  if (prints.some((expr) => expr.trim() === "" || expr.includes("\n"))) throw new UsageError("--print expects a one-line expression");
  return {
    location: resolveLocation(parseLocation(location), roots),
    then,
    prints,
    hits: positiveInteger(values.hits, "--hits", 1),
    timeoutSeconds: positiveInteger(values.timeout, "--timeout", 60),
    run: values.run,
  };
}

export interface Hit {
  n: number;
  event: HitEvent;
  threadId: string | undefined;
  stack: Frame[];
  locals: Locals;
  self: Evaluation;
  prints: { expr: string; result: Evaluation }[];
}

/** Frames listed in hits.md; the full stack is in the transcript. */
const STACK_FRAMES = 25;

/** The project's own frames — usually the ones that matter in a 150-frame Spring stack. */
const OWN_PACKAGE = "io.github.noahhhx.mimos.";

function shortMethod(method: string): string {
  return method.startsWith(OWN_PACKAGE) ? method.slice(OWN_PACKAGE.length) : method;
}

function variableLines(variables: Locals): string[] {
  if (variables.unavailable !== undefined) return [`(${variables.unavailable})`];
  const lines = [
    ...variables.arguments.map((v) => `${v.name} = ${v.value}    (argument)`),
    ...variables.locals.map((v) => `${v.name} = ${v.value}`),
  ];
  return lines.length > 0 ? lines : ["(none in scope)"];
}

function evaluationText(result: Evaluation): string {
  return result.ok ? result.value : `error: ${result.error}`;
}

export function formatHit(hit: Hit): string {
  const { event } = hit;
  const lines = [
    `## Hit ${hit.n} — \`${shortMethod(event.method)}\` line ${event.line}, thread \`${event.thread}\``,
    "",
  ];
  if (event.source !== undefined) lines.push(fenced(event.source, "java"), "");
  lines.push("### Locals", "", fenced(variableLines(hit.locals).join("\n")), "");
  lines.push("### `this`", "", fenced(evaluationText(hit.self)), "");
  if (hit.prints.length > 0) {
    lines.push("### Expressions", "", fenced(hit.prints.map((p) => `${p.expr} = ${evaluationText(p.result)}`).join("\n")), "");
  }
  const own = hit.stack.filter((frame) => frame.method.startsWith(OWN_PACKAGE));
  if (own.length > 0) {
    lines.push(
      "### Mimos frames",
      "",
      fenced(own.map((frame) => `[${frame.index}] ${shortMethod(frame.method)} (${frame.location ?? "no line info"})`).join("\n")),
      "",
    );
  }
  const shown = hit.stack.slice(0, STACK_FRAMES);
  lines.push(
    `### Stack (${shown.length} of ${hit.stack.length} frames)`,
    "",
    fenced(
      shown.map((frame) => `[${frame.index}] ${frame.method} (${frame.location ?? "no line info"})`).join("\n") +
        (hit.stack.length > shown.length ? `\n… ${hit.stack.length - shown.length} more in jdb.log` : ""),
    ),
  );
  return lines.join("\n");
}

/** `debug/1`, `debug/2`, … — one folder per debug session in the run. */
function sessionDir(run: Run): string {
  let n = 1;
  while (existsSync(join(run.dir, `debug/${n}`))) n++;
  return `debug/${n}`;
}

interface Trigger {
  argv: string[];
  exited: Promise<number>;
  output: string;
  code: number | undefined;
}

/** Runs `harness <argv> --run <run>` as a child, so its evidence lands in the same run. */
function startTrigger(argv: string[], run: Run): Trigger {
  const fullArgv = [...argv, "--run", run.dir];
  const child = spawn(process.execPath, [join(import.meta.dirname, "../cli.ts"), ...fullArgv], {
    cwd: REPO_ROOT,
    stdio: ["ignore", "pipe", "pipe"],
  });
  const trigger: Trigger = { argv, exited: Promise.resolve(1), output: "", code: undefined };
  child.stdout.on("data", (chunk: Buffer) => (trigger.output += chunk.toString("utf8")));
  child.stderr.on("data", (chunk: Buffer) => (trigger.output += chunk.toString("utf8")));
  trigger.exited = new Promise((resolve) => {
    child.on("error", (error) => {
      trigger.output += `\n${error.message}\n`;
      trigger.code = 1;
      resolve(1);
    });
    child.on("close", (code) => {
      trigger.code = code ?? 1;
      resolve(trigger.code);
    });
  });
  return trigger;
}

/** After the trigger exits, how long a hit may still take to arrive before the session gives up. */
const TRIGGER_GRACE_MS = 2_000;

async function threadIds(jdb: JdbSession, events: readonly HitEvent[]): Promise<(string | undefined)[]> {
  const threads = parseThreads(await jdb.send("threads", (text) => /Group /.test(text)));
  return events.map((event) => hitThreadId(threads, event.thread));
}

async function where(jdb: JdbSession): Promise<Frame[]> {
  return parseWhere(await jdb.send("where", (text) => /\[1\] |not|No /.test(text)));
}

function sameFrame(a: Frame | undefined, b: Frame | undefined): boolean {
  return a !== undefined && b !== undefined && a.method === b.method && a.location === b.location;
}

/**
 * Captures a batch of hits that are suspended together. Stack, locals, and
 * `this` come first for all of them because they run no code in the JVM; a
 * --print that calls a method does, and jdb invokes with every suspended
 * thread resumed until the call returns — so the other hits' threads may move
 * on. Their expressions are evaluated only if they are still where they hit.
 */
async function captureHits(jdb: JdbSession, first: number, events: readonly HitEvent[], prints: readonly string[]): Promise<Hit[]> {
  const ids = await threadIds(jdb, events);
  const hits: Hit[] = [];
  for (const [i, event] of events.entries()) {
    const threadId = ids[i];
    const n = first + i;
    process.stderr.write(`hit ${n}: ${event.method} line ${event.line} (${event.thread})\n`);
    if (threadId === undefined) {
      const error = `thread ${event.thread} not found in jdb's thread list`;
      hits.push({ n, event, threadId, stack: [], locals: { arguments: [], locals: [], unavailable: error }, self: { ok: false, error }, prints: [] });
      continue;
    }
    // `stop thread` events leave jdb without a current thread; select it for where/locals/print.
    await jdb.send(`thread ${threadId}`);
    const stack = await where(jdb);
    const locals = parseLocals(await jdb.send("locals", (text) => /Local variables:|not available|No /.test(text)));
    const self = parseEvaluation(await jdb.print("dump", "this"), "this");
    hits.push({ n, event, threadId, stack, locals, self, prints: [] });
  }
  for (const [i, hit] of hits.entries()) {
    if (hit.threadId === undefined || prints.length === 0) continue;
    await jdb.send(`thread ${hit.threadId}`);
    const moved = i > 0 && !sameFrame((await where(jdb))[0], hit.stack[0]);
    for (const expr of prints) {
      hit.prints.push({
        expr,
        result: moved
          ? { ok: false, error: "not evaluated — the thread moved on while an earlier hit's expression ran (jdb resumes all threads for a method call)" }
          : parseEvaluation(await jdb.print("print", expr), expr),
      });
    }
  }
  for (const hit of hits) if (hit.threadId !== undefined) await jdb.send(`resume ${hit.threadId}`);
  return hits;
}

/** Resumes threads that hit after the wanted hits were captured (detaching would too; this is explicit). */
async function releaseHits(jdb: JdbSession, events: readonly HitEvent[]): Promise<void> {
  for (const id of await threadIds(jdb, events)) if (id !== undefined) await jdb.send(`resume ${id}`);
}

/**
 * Captures hits in arrival order until enough were captured, the timeout
 * passes, or the trigger exited and a grace period passed without one.
 * Returns how many hits were released uncaptured (beyond `--hits`).
 */
async function collectHits(jdb: JdbSession, args: BreakArgs, trigger: Trigger | undefined, hits: Hit[]): Promise<number> {
  const deadline = Date.now() + args.timeoutSeconds * 1000;
  let triggerExitedAt: number | undefined;
  let released = 0;
  while (jdb.running) {
    if (parseHits(jdb.output).length > hits.length + released) {
      // Let the last event's source line and prompt arrive before reading it.
      await jdb.waitFor(endsWithPrompt, jdb.output.lastIndexOf("Breakpoint hit:"), 1_000);
      const pending = parseHits(jdb.output).slice(hits.length + released);
      const wanted = pending.slice(0, args.hits - hits.length);
      const extra = pending.slice(wanted.length);
      hits.push(...(await captureHits(jdb, hits.length + 1, wanted, args.prints)));
      await releaseHits(jdb, extra);
      released += extra.length;
      continue;
    }
    if (hits.length >= args.hits) break;
    if (trigger?.code !== undefined) triggerExitedAt ??= Date.now();
    const now = Date.now();
    if (now >= deadline || (triggerExitedAt !== undefined && now - triggerExitedAt >= TRIGGER_GRACE_MS)) break;
    await (trigger === undefined ? jdb.nextOutput(250) : Promise.race([jdb.nextOutput(250), trigger.exited]));
  }
  return released;
}

export const debugCommand: Command = {
  name: "debug",
  summary: "Stop the API at a breakpoint, capture stack and locals, resume",
  usage: `harness debug break <Class:line | Class.method[(argument types)]> [--then "<harness command>"]
                     [--print <expr>]... [--hits <n>] [--timeout <s>]

  <Class>             a simple name from the API's sources (MeController), Outer$Inner,
                      or a fully qualified name (needed for library classes)
  --then "<command>"  run this harness command once the breakpoint is set: ${TRIGGERS.join(" | ")},
                      e.g. --then "api GET /api/v1/me"; it records into the same run
  --print <expr>      evaluate at each hit, in the hitting frame (repeatable), e.g. --print 'profile.subjectId()'
  --hits <n>          hits to capture before detaching (default 1)
  --timeout <s>       give up after this many seconds (default 60); with --then, the session
                      also ends ${TRIGGER_GRACE_MS / 1000} s after the trigger exits
${RUN_USAGE}

Needs the debug overlay (harness up --debug): attaches jdb to the API's JDWP port
(localhost:${debugPorts().jdwp}). Only the thread that hits is suspended; each hit records the
stack, locals, \`dump this\`, and every --print, then the thread resumes. The breakpoint
is always cleared and jdb detaches, which resumes anything still suspended.
Writes debug/<n>/hits.md and the raw transcript debug/<n>/jdb.log (bearer tokens
redacted; other values as the JVM holds them). Exit code: 0 when the breakpoint was
hit, 1 when it was not hit or could not be set.`,
  async run(argv) {
    const args = parseBreakArgs(argv);
    const startedAt = new Date();
    // Attach before opening the run: a stack without the overlay leaves no empty run behind.
    const jdb = await JdbSession.attach({ port: debugPorts().jdwp, sourcepath: sourceRoots(REPO_ROOT) });
    const run = startRun("debug", startedAt, args.run);
    const dir = sessionDir(run);
    const where = jdbLocation(args.location);

    const section = [`## \`${displayCommand(["debug", ...argv])}\``, ""];
    const report = [`# Breakpoint \`${where}\``, ""];
    let exitCode = 1;
    let trigger: Trigger | undefined;
    const hits: Hit[] = [];
    let released = 0;
    let setFailure: string | undefined;
    let deferred = false;
    try {
      // jdb catches uncaught exceptions by default, suspending the whole VM; we only want our breakpoint.
      await jdb.send("ignore uncaught java.lang.Throwable");
      const stop = parseStopResult(await jdb.send(stopCommand(args.location), (text) => parseStopResult(text) !== undefined));
      if (stop === undefined || stop.kind === "failed") {
        setFailure = stop?.kind === "failed" ? stop.reason : "no answer from jdb";
      } else {
        deferred = stop.kind === "deferred";
        const setLine =
          stop.kind === "set"
            ? `\`${stopCommand(args.location)}\` — set; suspends only the hitting thread`
            : `\`${stopCommand(args.location)}\` — deferred: ${args.location.className} is not loaded yet; it is set when the class loads (a misspelled class never loads)`;
        section.push(`- **Breakpoint:** ${setLine}`);
        report.push(`- **Breakpoint:** ${setLine}`);
        process.stderr.write(`breakpoint ${stop.kind === "set" ? "set" : "deferred"}: ${where}\n`);
        if (args.then !== undefined) {
          trigger = startTrigger(args.then, run);
          process.stderr.write(`trigger: ${displayCommand(args.then)}\n`);
        }
        released = await collectHits(jdb, args, trigger, hits);
        await jdb.send(`clear ${where}`, (text) => /Removed:|Not found:/.test(text));
        // Hits that arrived after the wanted ones (or during the clear) are released, not captured.
        const late = parseHits(jdb.output).slice(hits.length + released);
        await releaseHits(jdb, late);
        released += late.length;
      }
      exitCode = hits.length > 0 ? 0 : 1;
    } finally {
      await jdb.quit();
      writeRunFile(run, `${dir}/jdb.log`, redactText(jdb.output));
      if (trigger !== undefined) {
        const code = await trigger.exited;
        writeRunFile(run, `${dir}/trigger.log`, redactText(trigger.output));
        const line = `\`${displayCommand(trigger.argv)}\` → exit ${code} (its section in summary.md; output in \`${dir}/trigger.log\`)`;
        section.push(`- **Trigger:** ${line}`);
        report.push(`- **Trigger:** ${line}`);
      }
    }

    if (released > 0) {
      section.push(`- **Released without capture:** ${released} more ${released === 1 ? "hit" : "hits"} beyond --hits ${args.hits}`);
    }
    const result =
      setFailure !== undefined
        ? `breakpoint could not be set — ${setFailure}`
        : hits.length > 0
        ? `hit ${hits.length} ${hits.length === 1 ? "time" : "times"} (wanted ${args.hits})`
        : `**not hit** within ${trigger?.code !== undefined ? `${TRIGGER_GRACE_MS / 1000} s of the trigger exiting` : `${args.timeoutSeconds} s`}` +
          (deferred
            ? ` — ${args.location.className} never loaded: check the name, or trigger code that uses the class`
            : " — the code never ran (e.g. the request was rejected earlier: check the trigger's status and the API log)");
    const health = await send("GET", new URL("/actuator/health", endpoints().api), {}).then(
      (response) => `${response.status} ${response.body.includes('"UP"') ? "UP" : response.body.slice(0, 80)}`,
      (error: Error) => `unreachable — ${error.message}`,
    );
    section.push(`- **Result:** ${result}`, `- **API after detaching:** \`/actuator/health\` ${health}`, `- **Evidence:** \`${dir}/hits.md\`, \`${dir}/jdb.log\``);
    report.push(`- **Result:** ${result}`, `- **API after detaching:** \`/actuator/health\` ${health}`, "");
    for (const hit of hits) report.push(formatHit(hit), "");

    const first = hits[0];
    if (first !== undefined) {
      section.push("", `Hit 1 — \`${shortMethod(first.event.method)}\` line ${first.event.line}:`, "", fenced(redactText(variableLines(first.locals).join("\n"))));
    }
    writeRunFile(run, `${dir}/hits.md`, redactText(report.join("\n")));
    appendSummary(run, redactText(section.join("\n")));
    await finishRun(run, ["debug", ...argv], startedAt, exitCode);
    process.stdout.write(`${result.replaceAll("**", "")}\n${join(run.dir, dir, "hits.md")}\n`);
    return exitCode;
  },
};
