import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { join, relative, sep } from "node:path";

import { HarnessError, UsageError } from "./args.ts";

/**
 * jdb, driven non-interactively: where breakpoints go (`Class:line` or
 * `Class.method`), the API's source roots, a session over jdb's prompt-based
 * console, and parsers for the output of the handful of commands
 * `harness debug break` sends. The parsers are pure, tested against
 * transcripts recorded from the running API (test/jdb.test.ts).
 */

// ---- breakpoint locations ----

export type BreakLocation =
  | { kind: "line"; className: string; line: number }
  | { kind: "method"; className: string; method: string; signature: string | undefined };

const IDENT = String.raw`[A-Za-z_$][\w$]*`;
const CLASS = new RegExp(String.raw`^${IDENT}(?:\.${IDENT})*$`);

/** `MeController:27`, `MeController.getMe`, `a.b.Outer$Inner.run(int, java.lang.String)` — class names unresolved. */
export function parseLocation(raw: string): BreakLocation {
  const atLine = /^(.+):(\d+)$/.exec(raw);
  if (atLine) {
    const [, className = "", line = ""] = atLine;
    if (!CLASS.test(className) || Number(line) < 1) throw new UsageError(`not a Class:line location: ${raw}`);
    return { kind: "line", className, line: Number(line) };
  }
  const inMethod = new RegExp(String.raw`^(${IDENT}(?:\.${IDENT})*)\.(${IDENT}|<init>|<clinit>)(\([^()]*\))?$`).exec(raw);
  if (!inMethod) throw new UsageError(`expected Class:line or Class.method[(argument types)], got: ${raw}`);
  const [, className = "", method = "", signature] = inMethod;
  return { kind: "method", className, method, signature };
}

/** How jdb names the location (`stop`/`clear` take it, `Set breakpoint …` echoes it). */
export function jdbLocation(location: BreakLocation): string {
  return location.kind === "line"
    ? `${location.className}:${location.line}`
    : `${location.className}.${location.method}${location.signature ?? ""}`;
}

/**
 * Suspends only the thread that hits (`stop thread`), so health checks and
 * other requests keep being served while a hit is inspected.
 */
export function stopCommand(location: BreakLocation): string {
  return `stop thread ${location.kind === "line" ? "at" : "in"} ${jdbLocation(location)}`;
}

/** The API's Java source roots (hand-written and generated), for `-sourcepath` and class lookup. */
export function sourceRoots(repoRoot: string): string[] {
  const modules = ["apps/api"];
  for (const parent of ["core", "integrations"]) {
    const dir = join(repoRoot, parent);
    if (!existsSync(dir)) continue;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.isDirectory()) modules.push(`${parent}/${entry.name}`);
    }
  }
  return modules
    .flatMap((module) => [`${module}/src/main/java`, `${module}/target/generated-sources/openapi/src/main/java`])
    .map((root) => join(repoRoot, root))
    .filter((root) => existsSync(root));
}

function javaFiles(dir: string, name: string, found: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) javaFiles(path, name, found);
    else if (entry.name === name) found.push(path);
  }
  return found;
}

/**
 * Qualifies a simple class name (`MeController`, `Outer$Inner`) from the
 * source roots; a dotted name is taken as fully qualified (library classes
 * too). Ambiguous or unknown simple names are usage errors.
 */
export function resolveClassName(className: string, roots: readonly string[]): string {
  if (className.includes(".")) return className;
  const [outer = className, ...nested] = className.split("$");
  const candidates = [
    ...new Set(
      roots.flatMap((root) =>
        javaFiles(root, `${outer}.java`).map((file) =>
          relative(root, file).slice(0, -".java".length).split(sep).join("."),
        ),
      ),
    ),
  ].map((name) => [name, ...nested].join("$"));
  if (candidates.length === 0) {
    throw new UsageError(`no class ${className} in the API's sources — give a fully qualified name for library classes`);
  }
  if (candidates.length > 1) {
    throw new UsageError(`${className} is ambiguous — qualify it: ${candidates.sort().join(", ")}`);
  }
  return candidates[0] ?? className;
}

export function resolveLocation(location: BreakLocation, roots: readonly string[]): BreakLocation {
  return { ...location, className: resolveClassName(location.className, roots) };
}

// ---- parsers ----

/** jdb's prompt: `> ` without a current thread, `<thread>[<frame>] ` with one. */
export function endsWithPrompt(text: string): boolean {
  return /(?:^|[\s\]])(?:>|\S*\[\d+\]) $/.test(text);
}

export type StopResult =
  | { kind: "set" }
  | { kind: "deferred" }
  | { kind: "failed"; reason: string };

/** The answer to `stop …`: set now, deferred until the class loads, or refused (with jdb's reason). */
export function parseStopResult(text: string): StopResult | undefined {
  const failed = /Unable to set breakpoint [^\n]*?: ([^\n]+)/.exec(text);
  if (failed) return { kind: "failed", reason: failed[1]?.trim() ?? "" };
  if (/Deferring breakpoint /.test(text)) return { kind: "deferred" };
  if (/Set breakpoint /.test(text)) return { kind: "set" };
  const other = /^(?:> )?(\S[^\n]*)$/m.exec(text.replace(/(?:^|\n)> ?$/, "").trim());
  return other ? { kind: "failed", reason: other[1] ?? "" } : undefined;
}

export interface HitEvent {
  thread: string;
  /** `io.github…MeController.getMe()` as jdb prints it. */
  method: string;
  line: number;
  bci: number;
  /** The source line jdb prints under the event, when it found the source. */
  source: string | undefined;
}

const HIT = /Breakpoint hit: "thread=(.*?)", (.+?), line=(-?[\d,]+) bci=(\d+)\n(?:(\d[\d,]*\s.*)\n)?/g;

/** Every `Breakpoint hit:` event in a stretch of jdb output, in order. */
export function parseHits(text: string): HitEvent[] {
  return [...text.matchAll(HIT)].map((match) => ({
    thread: match[1] ?? "",
    method: match[2] ?? "",
    line: Number((match[3] ?? "").replaceAll(",", "")),
    bci: Number(match[4]),
    source: match[5]?.trimEnd(),
  }));
}

export interface ThreadInfo {
  id: string;
  name: string;
  status: string;
}

/** `threads`: `  (java.lang.Thread)12330    Signal Dispatcher    running` — names may contain spaces. */
export function parseThreads(text: string): ThreadInfo[] {
  return [...text.matchAll(/^ +\([\w.$]+\)(\S+) +(.+?) {2,}(\S.*?) *$/gm)].map((match) => ({
    id: match[1] ?? "",
    name: match[2] ?? "",
    status: match[3] ?? "",
  }));
}

/** The thread suspended at the breakpoint with this name (its ID is what `thread`/`resume` take). */
export function hitThreadId(threads: readonly ThreadInfo[], name: string): string | undefined {
  const named = threads.filter((thread) => thread.name === name);
  return (named.find((thread) => thread.status.includes("at breakpoint")) ?? named[0])?.id;
}

export interface Frame {
  index: number;
  /** `io.github…MeController.getMe` */
  method: string;
  /** `MeController.java:27`, or null when jdb has no line information. */
  location: string | null;
}

/** `where`: `  [1] io.github…MeController.getMe (MeController.java:27)`; jdb groups digits (`1,000`). */
export function parseWhere(text: string): Frame[] {
  return [...text.matchAll(/\[(\d+)\] (\S+) \(([^)]*)\)/g)].map((match) => ({
    index: Number(match[1]),
    method: match[2] ?? "",
    location: match[3] === "null" || match[3] === undefined ? null : match[3].replace(/(?<=:\d+),(?=\d{3})/g, ""),
  }));
}

export interface Variable {
  name: string;
  value: string;
}

export interface Locals {
  arguments: Variable[];
  locals: Variable[];
  /** jdb's reason when it cannot list them (classes compiled without -g, native method, …). */
  unavailable: string | undefined;
}

/** `locals`: `Method arguments:` and `Local variables:` sections of `name = value` lines. */
export function parseLocals(text: string): Locals {
  const result: Locals = { arguments: [], locals: [], unavailable: undefined };
  let section: Variable[] | undefined;
  for (const raw of text.split("\n")) {
    const line = raw.replace(/^(?:> |\S*\[\d+\] )+/, "");
    if (line.startsWith("Method arguments:")) section = result.arguments;
    else if (line.startsWith("Local variables:")) section = result.locals;
    else if (/not available|No current thread|No default thread|Current thread isn't suspended/i.test(line)) {
      result.unavailable = line.trim();
    } else if (section) {
      const variable = /^(\S+) = (.*)$/.exec(line);
      if (variable) section.push({ name: variable[1] ?? "", value: variable[2] ?? "" });
    }
  }
  return result;
}

export type Evaluation = { ok: true; value: string } | { ok: false; error: string };

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Where ` <expr> = ` starts in print/dump output, after any prompt jdb wrote on the same line. */
function evaluationStart(text: string, expr: string): RegExpExecArray | null {
  return new RegExp(String.raw`(?:^|\n)(?:> |\S*\[\d+\] )* ?${escapeRegExp(expr)} = `).exec(text);
}

/** True once a `print`/`dump` of `expr` has answered (jdb evaluates these asynchronously). */
export function evaluationDone(text: string, expr: string): boolean {
  const start = evaluationStart(text, expr);
  if (!start) return false;
  const value = text.slice(start.index + start[0].length);
  // A dumped object spans lines until its closing brace.
  return value.startsWith("{") ? /\n\}/.test(value) : value.includes("\n");
}

/**
 * `print`/`dump`: ` expr = value` (a dumped object spans lines up to `}`).
 * A failure prints the exception first and then ` expr = null`.
 */
export function parseEvaluation(text: string, expr: string): Evaluation {
  const start = evaluationStart(text, expr);
  const before = start ? text.slice(0, start.index) : text;
  const error = /(?:^|\n)(?:> |\S*\[\d+\] )*((?:[\w.$]+(?:Exception|Error)|Exception)[^\n]*)/.exec(before)?.[1];
  if (error !== undefined) return { ok: false, error: error.replace(/^com\.sun\.tools\.example\.debug\.expr\./, "").trim() };
  if (!start) {
    const said = before.replace(/(?:^|\n)(?:> |\S*\[\d+\] )+/g, "\n").trim();
    return { ok: false, error: said || "no answer from jdb" };
  }
  let value = text.slice(start.index + start[0].length);
  value = value.startsWith("{") ? value.slice(0, (value.search(/\n\}/) + 2) || undefined) : (value.split("\n")[0] ?? "");
  return { ok: true, value: value.trimEnd() };
}

// ---- session ----

export interface JdbOptions {
  port: number;
  sourcepath: readonly string[];
  /** Called with every chunk jdb writes (the transcript). */
  onOutput?: (chunk: string) => void;
}

const QUIET_MS = 150;

/**
 * A jdb process attached to a JVM. Commands are sent one at a time and each
 * waits for its answer — jdb evaluates print/dump on a separate thread, so
 * overlapping them interleaves (and can corrupt) their output. Events such as
 * breakpoint hits arrive asynchronously in the same stream; `onOutput` and
 * `output` see everything.
 */
export class JdbSession {
  readonly child: ChildProcessWithoutNullStreams;
  /** Everything jdb has written so far. */
  output = "";
  #exited: Promise<number>;
  #exitCode: number | undefined;
  #listeners = new Set<() => void>();
  #lastOutputAt = 0;

  private constructor(child: ChildProcessWithoutNullStreams, onOutput: ((chunk: string) => void) | undefined) {
    this.child = child;
    const take = (chunk: Buffer): void => {
      const text = chunk.toString("utf8");
      this.output += text;
      this.#lastOutputAt = Date.now();
      onOutput?.(text);
      for (const listener of this.#listeners) listener();
    };
    child.stdout.on("data", take);
    child.stderr.on("data", take);
    this.#exited = new Promise((resolve, reject) => {
      child.on("error", (error: NodeJS.ErrnoException) => {
        reject(
          error.code === "ENOENT"
            ? new HarnessError("jdb not found on PATH — run the harness as `devenv shell -- harness …`")
            : error,
        );
      });
      child.on("close", (code) => {
        this.#exitCode = code ?? 1;
        for (const listener of this.#listeners) listener();
        resolve(this.#exitCode);
      });
    });
    // Callers observe exit through waitFor/exited; never leave the rejection unhandled.
    this.#exited.catch(() => undefined);
  }

  /** Attaches to localhost:<port> and waits for jdb's first prompt. */
  static async attach(options: JdbOptions): Promise<JdbSession> {
    const args = ["-attach", `localhost:${options.port}`];
    if (options.sourcepath.length > 0) args.push("-sourcepath", options.sourcepath.join(":"));
    const session = new JdbSession(spawn("jdb", args, { stdio: ["pipe", "pipe", "pipe"] }), options.onOutput);
    const ready = await Promise.race([
      session.waitFor((text) => /Initializing jdb \.\.\./.test(text) && endsWithPrompt(text), 0, 20_000),
      session.#exited.then(() => false),
    ]);
    if (!ready || session.#exitCode !== undefined) {
      session.kill();
      // The cause and jdb's verdict, not the stack trace between them.
      const said = session.output
        .split("\n")
        .filter((line) => /\S/.test(line) && !/^\s+at /.test(line))
        .slice(-4)
        .join("\n");
      throw new HarnessError(
        `jdb could not attach to localhost:${options.port} — is the stack up with the debug overlay (harness up --debug),` +
          ` and no other debugger attached (JDWP takes one at a time)?${said ? `\njdb said:\n${said}` : ""}`,
      );
    }
    return session;
  }

  get running(): boolean {
    return this.#exitCode === undefined;
  }

  /**
   * Resolves true once `done(output since offset)` holds and jdb has been
   * quiet for a moment; false on timeout or when jdb exits.
   */
  waitFor(done: (text: string) => boolean, offset: number, timeoutMs: number): Promise<boolean> {
    return new Promise((resolve) => {
      let quietTimer: NodeJS.Timeout | undefined;
      const finish = (result: boolean): void => {
        clearTimeout(deadline);
        clearTimeout(quietTimer);
        this.#listeners.delete(check);
        resolve(result);
      };
      const check = (): void => {
        if (this.#exitCode !== undefined) return finish(false);
        clearTimeout(quietTimer);
        if (!done(this.output.slice(offset))) return;
        const wait = Math.max(0, this.#lastOutputAt + QUIET_MS - Date.now());
        quietTimer = setTimeout(() => (done(this.output.slice(offset)) ? finish(true) : undefined), wait);
      };
      const deadline = setTimeout(() => finish(false), timeoutMs);
      this.#listeners.add(check);
      check();
    });
  }

  /** Resolves when jdb writes anything, or after `ms`. */
  nextOutput(ms: number): Promise<void> {
    return new Promise((resolve) => {
      const done = (): void => {
        clearTimeout(timer);
        this.#listeners.delete(done);
        resolve();
      };
      const timer = setTimeout(done, ms);
      this.#listeners.add(done);
    });
  }

  /**
   * Sends one command and returns its output: everything up to the next
   * prompt, or up to `done` holding (print/dump), whichever the caller asks for.
   */
  async send(command: string, done: (text: string) => boolean = endsWithPrompt, timeoutMs = 10_000): Promise<string> {
    if (!this.running) throw new HarnessError(`jdb exited before \`${command}\``);
    const offset = this.output.length;
    this.child.stdin.write(`${command}\n`);
    const answered = await this.waitFor((text) => done(text) && endsWithPrompt(text), offset, timeoutMs);
    const text = this.output.slice(offset);
    return answered ? text : `${text}\n(no answer from jdb within ${timeoutMs / 1000} s)`;
  }

  print(command: "print" | "dump", expr: string): Promise<string> {
    return this.send(`${command} ${expr}`, (text) => evaluationDone(text, expr) || /Exception|No current thread|No default thread/.test(text), 15_000);
  }

  /** Detaches: jdb's quit disposes the connection, which drops our requests and resumes every thread. */
  async quit(): Promise<void> {
    if (this.running) this.child.stdin.end("quit\n");
    const timer = setTimeout(() => this.kill(), 5_000);
    await this.#exited.catch(() => 1);
    clearTimeout(timer);
  }

  kill(): void {
    if (this.running) this.child.kill("SIGKILL");
  }
}
