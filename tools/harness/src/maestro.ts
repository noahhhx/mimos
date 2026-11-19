import { existsSync, readdirSync } from "node:fs";
import { join } from "node:path";

import { load } from "js-yaml";

import { REPO_ROOT } from "./config.ts";

export const FLOWS_DIR = join(REPO_ROOT, "apps/mobile/flows");

export interface FlowEnv {
  MIMOS_USER: string;
  MIMOS_PASSWORD: string;
  MIMOS_WEB_ADDRESS: string;
  /** The other member's username, which the app shows as their name, for a flow tagged `household`. */
  MIMOS_PARTNER?: string;
}

/** The tag that has a flow sign in as one of two members of a household. */
export const HOUSEHOLD_TAG = "household";

/** Whether a flow's config (the part before its first `---`) carries the `household` tag. */
export function wantsHousehold(flowYaml: string): boolean {
  const config = load(flowYaml.split(/^---\s*$/m)[0] ?? "") as { tags?: unknown } | null;
  return Array.isArray(config?.tags) && config.tags.includes(HOUSEHOLD_TAG);
}

export interface AppRequest {
  requestId: string;
  method: string;
  path: string;
}

export function flowNames(dir = FLOWS_DIR): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((file) => file.endsWith(".yaml"))
    .map((file) => file.slice(0, -".yaml".length))
    .sort();
}

export function maestroArgs(input: { serial: string; flowFile: string; env: FlowEnv; staging: string }): string[] {
  const { serial, flowFile, env, staging } = input;
  return [
    "test",
    "--udid",
    serial,
    "--no-ansi",
    "--format",
    "JUNIT",
    "--output",
    join(staging, "report.xml"),
    "--test-output-dir",
    join(staging, "output"),
    "--debug-output",
    join(staging, "debug"),
    "--flatten-debug-output",
    ...Object.entries(env).flatMap(([key, value]) => ["-e", `${key}=${value}`]),
    flowFile,
  ];
}

const XML_ENTITIES: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };

function decodeXml(text: string): string {
  return text.replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (whole, entity: string) => {
    if (entity.startsWith("#")) {
      const code = entity[1]?.toLowerCase() === "x" ? parseInt(entity.slice(2), 16) : Number(entity.slice(1));
      return Number.isSafeInteger(code) && code >= 0 && code <= 0x10ffff ? String.fromCodePoint(code) : whole;
    }
    return XML_ENTITIES[entity.toLowerCase()] ?? whole;
  });
}

export function maestroFailure(junitXml: string): string | undefined {
  const match = /<(?:failure|error)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/(?:failure|error)>)/.exec(junitXml);
  if (match === null) return undefined;
  const attribute = /\bmessage="([^"]*)"/.exec(match[1] ?? "")?.[1];
  const text = decodeXml(match[2] ?? "").trim();
  const failure = text !== "" ? text : attribute !== undefined ? decodeXml(attribute).trim() : "";
  return failure === "" ? "the flow failed (the report gives no message)" : failure;
}

export const REQUEST_TAG = "MimosRequest";
const REQUEST_FIELDS = /\brequestId=([\w-]+) method=([A-Z]+) path=(\S+)/;

export function parseAppRequests(logcat: string): { requests: AppRequest[]; unreadable: string[] } {
  const requests = new Map<string, AppRequest>();
  const unreadable: string[] = [];
  for (const line of logcat.split("\n")) {
    if (!line.includes(REQUEST_TAG)) continue;
    const fields = REQUEST_FIELDS.exec(line);
    if (fields === null) {
      unreadable.push(line.trim());
      continue;
    }
    const [, requestId, method, path] = fields as unknown as [string, string, string, string];
    if (!requests.has(requestId)) requests.set(requestId, { requestId, method, path });
  }
  return { requests: [...requests.values()], unreadable };
}

export interface FlowFailure {
  at: string;
  message: string;
}

export const lastLines = (text: string, count: number): string => text.trimEnd().split("\n").slice(-count).join("\n");

export function flowFailure(input: {
  junit: string | undefined;
  code: number;
  output: string;
  requests: readonly AppRequest[];
  unreadable: readonly string[];
  requestLog: string;
}): FlowFailure | undefined {
  const assertion = input.junit === undefined ? undefined : maestroFailure(input.junit);
  if (assertion !== undefined) return { at: assertion.split("\n")[0] ?? assertion, message: assertion };
  if (input.code !== 0) return { at: "Maestro", message: lastLines(input.output, 30) || `maestro exited ${input.code} with no output` };
  if (input.requests.length > 0) return undefined;
  const unexpected = input.unreadable.length > 0 ? `\nLines in an unexpected format:\n${lastLines(input.unreadable.join("\n"), 5)}` : "";
  return {
    at: "Request evidence",
    message:
      `The flow passed but ${input.requestLog} holds no request the app sent, so the API's side cannot be found. ` +
      `The debug app logs them as ${REQUEST_TAG} lines (androidApp RequestLog.kt).${unexpected}`,
  };
}
