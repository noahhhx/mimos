import { appendFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

import { HarnessError } from "./args.ts";
import { exec, type ExecResult } from "./exec.ts";
import { REPO_ROOT } from "./config.ts";

/**
 * The last release, for running main's Android flows against the server
 * people still run (docs/harness/releasing.md). Imports
 * only Node's own modules, so CI runs `node tools/harness/src/release.ts
 * pick` before it installs anything.
 */

export const SELFHOST_COMPOSE = "deploy/selfhost/compose.yml";
export const REALM_EXPORT = "deploy/keycloak/mimos-realm.json";
export const FLOWS_PATH = "apps/mobile/flows";
export const MOBILE_CLIENT = "mimos-mobile";
/** What `publish` pushes for a tag, each as `ghcr.io/noahhhx/<name>:<version>`. */
export const RELEASE_IMAGES = ["mimos-api", "mimos-web", "mimos-keycloak", "mimos-country-week"] as const;

export interface Release {
  tag: string;
  /** The tag without its `v`, as `publish` tags the images and the self-host `.env` names them. */
  version: string;
}

export function releaseOf(tag: string): Release {
  if (!/^v\d+\.\d+\.\d+(-rc\.\d+)?$/.test(tag)) {
    throw new HarnessError(`${tag} is not a release tag (v1.2.3 or v1.2.3-rc.1)`);
  }
  return { tag, version: tag.slice(1) };
}

export function imageRef(name: string, version: string): string {
  return `ghcr.io/noahhhx/${name}:${version}`;
}

export function hasMobileClient(realmJson: string): boolean {
  const realm = JSON.parse(realmJson) as { clients?: { clientId?: unknown }[] };
  return (realm.clients ?? []).some((client) => client.clientId === MOBILE_CLIENT);
}

/** Flow names from `git ls-tree --name-only` output or a directory listing. */
export function flowNamesIn(paths: readonly string[]): string[] {
  return paths
    .map((path) => path.slice(path.lastIndexOf("/") + 1))
    .filter((file) => file.endsWith(".yaml"))
    .map((file) => file.slice(0, -".yaml".length))
    .sort();
}

export type Pick = { kind: "run"; release: Release; flows: string[] } | { kind: "skip"; reason: string };

export interface PickInput {
  /** The last `v*` tag reachable from HEAD, as the contract check finds it; undefined when there is none. */
  tag: string | undefined;
  realmAtTag: string | undefined;
  /** Images of RELEASE_IMAGES the registry does not have for the tag's version. */
  missingImages: readonly string[];
  flowsAtTag: readonly string[];
  flowsNow: readonly string[];
}

/**
 * Runs main's flows that the release already had: a flow added since tests
 * something the release may lack, and gets its first run against the next
 * release.
 */
export function decide(input: PickInput): Pick {
  if (input.tag === undefined)
    return { kind: "skip", reason: "No v* release yet; nothing to run the app's flows against." };
  const release = releaseOf(input.tag);
  if (input.realmAtTag === undefined || !hasMobileClient(input.realmAtTag)) {
    return {
      kind: "skip",
      reason: `${release.tag} predates the ${MOBILE_CLIENT} Keycloak client, so the app cannot sign in to it.`,
    };
  }
  if (input.missingImages.length > 0) {
    return { kind: "skip", reason: `${release.tag}'s images are not published (${input.missingImages.join(", ")}).` };
  }
  const flows = input.flowsNow.filter((flow) => input.flowsAtTag.includes(flow));
  if (flows.length === 0) return { kind: "skip", reason: `${release.tag} has none of main's flows.` };
  return { kind: "run", release, flows };
}

/** `key=value` lines for `$GITHUB_OUTPUT`; a skip writes none, so the job's later steps see an empty `tag`. */
export function outputLines(pick: Pick): string {
  if (pick.kind === "skip") return "";
  return `tag=${pick.release.tag}\nversion=${pick.release.version}\nflows=${pick.flows.join(" ")}\n`;
}

function git(args: readonly string[]): Promise<ExecResult> {
  return exec("git", args, { cwd: REPO_ROOT });
}

export async function lastReleaseTag(): Promise<string | undefined> {
  const result = await git(["describe", "--tags", "--abbrev=0", "--match", "v*"]);
  return result.code === 0 ? result.stdout.trim() : undefined;
}

/** A file as the tag has it; undefined when the tag has no such file. */
export async function fileAt(tag: string, path: string): Promise<string | undefined> {
  const result = await git(["show", `${tag}:${path}`]);
  return result.code === 0 ? result.stdout : undefined;
}

async function filesAt(tag: string, path: string): Promise<string[]> {
  const result = await git(["ls-tree", "--name-only", `${tag}:${path}`]);
  return result.code === 0 ? result.stdout.split("\n").filter(Boolean) : [];
}

async function missingImages(version: string): Promise<string[]> {
  const missing: string[] = [];
  for (const name of RELEASE_IMAGES) {
    const result = await exec("docker", ["manifest", "inspect", imageRef(name, version)]);
    if (result.code !== 0) missing.push(name);
  }
  return missing;
}

export async function pickRelease(): Promise<Pick> {
  const tag = await lastReleaseTag();
  if (tag === undefined) return decide({ tag, realmAtTag: undefined, missingImages: [], flowsAtTag: [], flowsNow: [] });
  const { version } = releaseOf(tag);
  const [realmAtTag, flowsAtTag, flowsNow] = await Promise.all([
    fileAt(tag, REALM_EXPORT),
    filesAt(tag, FLOWS_PATH),
    filesAt("HEAD", FLOWS_PATH),
  ]);
  const realmReady = realmAtTag !== undefined && hasMobileClient(realmAtTag);
  return decide({
    tag,
    realmAtTag,
    missingImages: realmReady ? await missingImages(version) : [],
    flowsAtTag: flowNamesIn(flowsAtTag),
    flowsNow: flowNamesIn(flowsNow),
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv[2] !== "pick" || process.argv.length > 3) {
    process.stderr.write("usage: node tools/harness/src/release.ts pick\n");
    process.exit(2);
  }
  const pick = await pickRelease();
  if (pick.kind === "skip") {
    process.stdout.write(`::notice::${pick.reason}\n`);
  } else {
    process.stdout.write(`${pick.release.tag}: ${pick.flows.length} flows (${pick.flows.join(", ")})\n`);
    if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, outputLines(pick));
  }
}
