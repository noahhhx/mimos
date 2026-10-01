import { statSync } from "node:fs";
import { dirname, join } from "node:path";

import { execOk } from "./exec.ts";

/**
 * Stale-image detection for `harness status`: an image is stale when any of
 * its service's build inputs (config.ts BUILD_INPUTS) changed after the image
 * was created.
 *
 * "Changed" is the newest file mtime under those paths — not the newest
 * commit time, which would flag an image built from the working tree and
 * committed afterwards. Checkouts, pulls, and stash pops rewrite files, so
 * they count as changes too. Deleted files have no mtime; their nearest
 * surviving parent directory's mtime (which a deletion updates) stands in.
 */

export interface SourceProbe {
  /** Tracked and untracked (not ignored) files under the paths, repo-relative. */
  files(paths: readonly string[]): Promise<string[]>;
  /** Tracked files under the paths that are deleted in the working tree. */
  deleted(paths: readonly string[]): Promise<string[]>;
  /** Modification time, or undefined when the path does not exist. */
  mtime(path: string): Date | undefined;
}

export interface SourceChange {
  time: Date;
  path: string;
}

export async function latestSourceChange(paths: readonly string[], probe: SourceProbe): Promise<SourceChange | undefined> {
  const [files, deleted] = await Promise.all([probe.files(paths), probe.deleted(paths)]);
  let latest: SourceChange | undefined;
  const consider = (path: string, time: Date | undefined): void => {
    if (time && (!latest || time > latest.time)) latest = { time, path };
  };
  const deletedSet = new Set(deleted);
  for (const file of files) {
    if (!deletedSet.has(file)) consider(file, probe.mtime(file));
  }
  for (const file of deleted) {
    let parent = dirname(file);
    while (parent !== "." && probe.mtime(parent) === undefined) parent = dirname(parent);
    consider(`${file} (deleted)`, probe.mtime(parent));
  }
  return latest;
}

export type Freshness =
  | { kind: "no-image" }
  | { kind: "undated" }
  | { kind: "fresh" }
  | { kind: "stale"; change: SourceChange };

/**
 * Images built with SOURCE_DATE_EPOCH set (a Nix shell exports 1980-01-01)
 * carry that date instead of their build time; anything this old can't be
 * compared. The harness unsets it for its own builds.
 */
const UNDATED_BEFORE = new Date("2000-01-01T00:00:00Z");

export function freshness(imageCreated: Date | undefined, change: SourceChange | undefined): Freshness {
  if (!imageCreated) return { kind: "no-image" };
  if (imageCreated < UNDATED_BEFORE) return { kind: "undated" };
  if (change && change.time > imageCreated) return { kind: "stale", change };
  return { kind: "fresh" };
}

/** The real probe: git for the file lists (so ignored build outputs don't count), fs for times. */
export function gitProbe(root: string): SourceProbe {
  const lines = (text: string): string[] => text.split("\n").filter(Boolean);
  return {
    files: async (paths) => lines(await execOk("git", ["ls-files", "-co", "--exclude-standard", "--", ...paths], { cwd: root })),
    deleted: async (paths) => lines(await execOk("git", ["ls-files", "-d", "--", ...paths], { cwd: root })),
    mtime: (path) => statSync(join(root, path), { throwIfNoEntry: false })?.mtime,
  };
}
