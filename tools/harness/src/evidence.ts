import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { extname, join, relative } from "node:path";

import { redactBody, redactHar, redactText } from "./redact.ts";
import { readZip, writeZip } from "./zip.ts";

/**
 * Browser evidence is produced by Playwright outside the run folder and
 * copied in through here, redacted by kind (principle 5): HARs and trace
 * network records structurally, other text as free text, media as-is. So
 * nothing unredacted is ever written under .harness/runs/.
 */

const BINARY = /\.(png|jpe?g|webp|gif|ico|svg|woff2?|ttf|otf|eot|webm|mp4|wasm|bin|pdf)$/i;

function isText(name: string, data: Buffer): boolean {
  return !BINARY.test(name) && !data.includes(0);
}

/** One JSON value per line (trace.network, *.trace, console.jsonl); unparseable lines as free text. */
function redactJsonLines(text: string): string {
  return text
    .split("\n")
    .map((line) => {
      if (line.trim() === "") return line;
      try {
        return JSON.stringify(redactHar(JSON.parse(line)));
      } catch {
        return redactText(line);
      }
    })
    .join("\n");
}

/** `a=1&b=2` — how a trace stores a form post's body (a resource with no media type). */
const FORM_BODY = /^[^\s&=]+=[^\s&]*(&[^\s&=]+=[^\s&]*)*$/;

function redactTextFile(name: string, text: string): string {
  if (/\.(trace|network|jsonl)$/.test(name)) return redactJsonLines(text);
  if (name.endsWith(".har")) return `${JSON.stringify(redactHar(JSON.parse(text)), null, 2)}\n`;
  if (name.endsWith(".json")) return redactBody(text, "application/json");
  if (FORM_BODY.test(text)) return redactBody(text, "application/x-www-form-urlencoded");
  return redactText(text);
}

/** A trace.zip with every text entry redacted (network records, snapshots, captured resources). */
export function redactTraceZip(zip: Buffer): Buffer {
  return writeZip(
    readZip(zip).map(({ name, data }) =>
      isText(name, data) ? { name, data: Buffer.from(redactTextFile(name, data.toString("utf8"))) } : { name, data },
    ),
  );
}

/** One evidence file's redacted bytes; `rewrite` adjusts text afterwards (e.g. staging paths → run paths). */
export function redactEvidence(name: string, data: Buffer, rewrite: (text: string) => string = (text) => text): Buffer {
  if (extname(name).toLowerCase() === ".zip") return redactTraceZip(data);
  if (!isText(name, data)) return data;
  return Buffer.from(rewrite(redactTextFile(name, data.toString("utf8"))));
}

/**
 * Copies a directory tree, redacting each file; returns the copied files'
 * paths relative to `to`. Dotfiles (Playwright's .last-run.json) are runner
 * state, not evidence, and are skipped.
 */
export function copyRedacted(from: string, to: string, rewrite?: (text: string) => string): string[] {
  const copied: string[] = [];
  for (const entry of readdirSync(from, { withFileTypes: true, recursive: true })) {
    if (!entry.isFile() || entry.name.startsWith(".")) continue;
    const source = join(entry.parentPath, entry.name);
    const path = relative(from, source);
    const target = join(to, path);
    mkdirSync(join(target, ".."), { recursive: true });
    writeFileSync(target, redactEvidence(entry.name, readFileSync(source), rewrite));
    copied.push(path);
  }
  return copied.sort();
}
