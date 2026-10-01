import { parseArgs, type ParseArgsConfig } from "node:util";

/** Bad invocation: the CLI prints the command's usage and exits 2. */
export class UsageError extends Error {}

/** A runtime failure worth a one-line message rather than a stack trace. */
export class HarnessError extends Error {}

/** `node:util` parseArgs, strict, with its errors turned into usage errors. */
export function parseCommandArgs<const T extends Omit<ParseArgsConfig, "strict">>(
  config: T,
): ReturnType<typeof parseArgs<T & { strict: true }>> {
  try {
    return parseArgs({ ...config, strict: true });
  } catch (error) {
    throw new UsageError((error as Error).message);
  }
}

/** `K:V` → [K, V]; an empty value (`K:`) is allowed and means "remove this header". */
export function parseHeader(raw: string): [string, string] {
  const colon = raw.indexOf(":");
  const name = colon > 0 ? raw.slice(0, colon).trim() : "";
  if (!/^[A-Za-z0-9!#$%&'*+.^_`|~-]+$/.test(name)) {
    throw new UsageError(`--header expects Name:Value, got: ${raw}`);
  }
  return [name, raw.slice(colon + 1).trim()];
}

/** Quotes argv for display (summary.md), so a reader can paste it back. */
export function displayCommand(argv: readonly string[]): string {
  return ["harness", ...argv]
    .map((arg) => (/^[\w@%+=:,./-]+$/.test(arg) ? arg : `'${arg.replaceAll("'", `'\\''`)}'`))
    .join(" ");
}
