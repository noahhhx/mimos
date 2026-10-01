#!/usr/bin/env node
import { readFileSync, realpathSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { HarnessError, UsageError } from "./args.ts";
import type { Command } from "./command.ts";
import { apiCommand } from "./commands/api.ts";
import { logsCommand } from "./commands/logs.ts";
import { downCommand, resetCommand, upCommand } from "./commands/stack.ts";
import { statusCommand } from "./commands/status.ts";
import { tokenCommand } from "./commands/token.ts";

/**
 * `harness <command>` — deploy, drive, observe, and debug the local stack.
 * Agents call it as `devenv shell -- harness <command>`; evidence goes to
 * .harness/runs/<run>/ with summary.md as the entry point. See docs/harness/.
 */

export const COMMANDS: readonly Command[] = [
  upCommand,
  downCommand,
  resetCommand,
  statusCommand,
  tokenCommand,
  apiCommand,
  logsCommand,
];

function version(): string {
  const manifest = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8")) as { version: string };
  return manifest.version;
}

function help(): string {
  const width = Math.max(...COMMANDS.map((command) => command.name.length));
  return [
    "usage: harness <command> [options]",
    "",
    ...COMMANDS.map((command) => `  ${command.name.padEnd(width)}  ${command.summary}`),
    "",
    "harness <command> --help for a command's options. Evidence: .harness/runs/latest/summary.md",
  ].join("\n");
}

export async function main(argv: string[]): Promise<number> {
  const [name, ...rest] = argv;
  if (name === undefined || name === "help" || name === "--help" || name === "-h") {
    process.stdout.write(`${help()}\n`);
    return name === undefined ? 2 : 0;
  }
  if (name === "--version") {
    process.stdout.write(`harness ${version()}\n`);
    return 0;
  }
  const command = COMMANDS.find((candidate) => candidate.name === name);
  if (!command) {
    process.stderr.write(`harness: unknown command ${name}\n\n${help()}\n`);
    return 2;
  }
  if (rest.includes("--help") || rest.includes("-h")) {
    process.stdout.write(`usage: ${command.usage}\n`);
    return 0;
  }
  try {
    return await command.run(rest);
  } catch (error) {
    if (error instanceof UsageError) {
      process.stderr.write(`harness ${name}: ${error.message}\n\nusage: ${command.usage}\n`);
      return 2;
    }
    if (error instanceof HarnessError) {
      process.stderr.write(`harness ${name}: ${error.message}\n`);
      return 1;
    }
    throw error;
  }
}

// Run when executed directly (import.meta.main needs Node 24); tests import main() instead.
if (process.argv[1] !== undefined && realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = await main(process.argv.slice(2));
}
