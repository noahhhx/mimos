import { randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";

import { envLines } from "../accounts.ts";
import { displayCommand, HarnessError, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { composeEnv } from "../compose.ts";
import { endpoints, FAKES_OVERLAY, keycloakAdmin, REPO_ROOT } from "../config.ts";
import { exec, type ExecResult } from "../exec.ts";
import { redactText } from "../redact.ts";
import { fileAt, hasMobileClient, lastReleaseTag, REALM_EXPORT, releaseOf, SELFHOST_COMPOSE, type Release } from "../release.ts";
import { appendSummary, writeRunFile } from "../run.ts";

/**
 * `harness release`: the last release's published images, booted from that
 * tag's deploy/selfhost compose file with this checkout's fakes, on the ports
 * the dev stack uses, so `harness mobile` flows run against it unchanged.
 */

export const RELEASE_PROJECT = "mimos-release";
/** The tag's compose file and its .env, kept so `down` finds the same stack. */
export const RELEASE_DIR = ".harness/release";

export interface ReleaseArgs {
  action: "up" | "down";
  tag: string | undefined;
  run: string | undefined;
}

export function parseReleaseArgs(argv: string[]): ReleaseArgs {
  const { values, positionals } = parseCommandArgs({ args: argv, allowPositionals: true, options: { ...RUN_OPTION } });
  const [action, tag, ...extra] = positionals;
  if (action !== "up" && action !== "down") throw new UsageError("expected an action: up | down");
  if (extra.length > 0 || (action === "down" && tag !== undefined)) {
    throw new UsageError(action === "up" ? "expected at most one tag" : "down takes no arguments");
  }
  if (action === "down" && values.run !== undefined) throw new UsageError("down records no run, so it takes no --run");
  return { action, tag, run: values.run };
}

/**
 * The self-host `.env` for a stack on this machine: localhost URLs, and the
 * admin login `harness user create` uses. The secret key is fresh each time,
 * so intervals.icu is on, as on a server that set one.
 */
export function releaseEnv(release: Release, secretKey: string, env: NodeJS.ProcessEnv = process.env): Record<string, string> {
  const urls = endpoints(env);
  const admin = keycloakAdmin(env);
  return {
    MIMOS_VERSION: release.version,
    POSTGRES_PASSWORD: "mimos",
    KEYCLOAK_ADMIN_USERNAME: admin.username,
    KEYCLOAK_ADMIN_PASSWORD: admin.password,
    WEB_ORIGIN: urls.web,
    API_PUBLIC_URL: urls.api,
    KEYCLOAK_PUBLIC_URL: urls.keycloak,
    MIMOS_SECRET_KEY: secretKey,
  };
}

/**
 * The project directory is deploy/docker, so the fakes overlay's build
 * context resolves as it does for the dev stack; the tag's compose file has
 * no relative paths of its own.
 */
export function releaseComposeArgs(dir: string, args: readonly string[]): string[] {
  return [
    "compose",
    "-p",
    RELEASE_PROJECT,
    "--project-directory",
    join(REPO_ROOT, "deploy/docker"),
    "-f",
    join(dir, "compose.yml"),
    "-f",
    join(REPO_ROOT, FAKES_OVERLAY),
    "--env-file",
    join(dir, ".env"),
    ...args,
  ];
}

function releaseCompose(dir: string, args: readonly string[], stream = false): Promise<ExecResult> {
  return exec("docker", releaseComposeArgs(dir, args), { cwd: REPO_ROOT, env: composeEnv(), stream });
}

async function up(args: ReleaseArgs, argv: string[]): Promise<number> {
  const tag = args.tag ?? (await lastReleaseTag());
  if (tag === undefined) throw new HarnessError("no v* tag is reachable from HEAD; name one: harness release up v1.2.3");
  const release = releaseOf(tag);
  const [compose, realm] = await Promise.all([fileAt(tag, SELFHOST_COMPOSE), fileAt(tag, REALM_EXPORT)]);
  if (compose === undefined) throw new HarnessError(`${tag} has no ${SELFHOST_COMPOSE}`);
  if (realm === undefined || !hasMobileClient(realm)) {
    throw new HarnessError(`${tag}'s realm has no mimos-mobile client, so the app cannot sign in to it`);
  }

  const startedAt = new Date();
  const run = startRun(`release-${release.version}`, startedAt, args.run);
  const section = [`## \`${displayCommand(["release", ...argv])}\``, ""];
  const finish = async (exitCode: number): Promise<number> => {
    appendSummary(run, section.join("\n"));
    await finishRun(run, ["release", ...argv], startedAt, exitCode);
    return exitCode;
  };

  const dir = join(REPO_ROOT, RELEASE_DIR);
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, "compose.yml"), compose);
  writeFileSync(join(dir, ".env"), `${envLines(releaseEnv(release, randomBytes(32).toString("base64")))}\n`, { mode: 0o600 });
  writeFileSync(join(dir, "tag"), `${tag}\n`);
  writeRunFile(run, "release/compose.yml", compose);
  section.push(`- ${tag}'s \`${SELFHOST_COMPOSE}\` with \`${FAKES_OVERLAY}\`, project \`${RELEASE_PROJECT}\` (\`release/compose.yml\`).`);

  const result = await releaseCompose(dir, ["up", "-d", "--wait", "--build"], true);
  writeRunFile(run, "release/up.log", redactText(result.stdout + result.stderr));
  const ps = await releaseCompose(dir, ["ps", "--all", "--format", "{{.Service}}\t{{.Image}}\t{{.State}}\t{{.Health}}"]);
  section.push(`- \`compose up --wait\` exited ${result.code} (\`release/up.log\`).`, "", fenced(ps.stdout));
  if (result.code === 0) {
    process.stderr.write(`harness: ${tag} is up and healthy\n`);
    return finish(0);
  }
  const logs = await releaseCompose(dir, ["logs", "--no-color", "--timestamps"]);
  writeRunFile(run, "release/logs.txt", redactText(logs.stdout + logs.stderr));
  section.push("", `**${tag} is not healthy.** Every service's logs are in \`release/logs.txt\`.`);
  process.stderr.write(`harness: ${tag} is not healthy; see summary.md\n`);
  return finish(1);
}

async function down(): Promise<number> {
  const dir = join(REPO_ROOT, RELEASE_DIR);
  if (!existsSync(join(dir, "compose.yml"))) {
    process.stderr.write("harness: no release stack was started here\n");
    return 0;
  }
  const tag = readFileSync(join(dir, "tag"), "utf8").trim();
  const result = await releaseCompose(dir, ["down", "-v", "--remove-orphans"], true);
  if (result.code !== 0) return 1;
  rmSync(dir, { recursive: true, force: true });
  process.stderr.write(`harness: ${tag}'s stack and its volumes are gone\n`);
  return 0;
}

export const releaseCommand: Command = {
  name: "release",
  summary: "Boot the last release's published images, for flows against it",
  usage: `harness release up [<tag>] | down

  up [<tag>]   boot <tag>'s published images (default: the last v* tag reachable from
               HEAD) from that tag's ${SELFHOST_COMPOSE}, with this checkout's
               ${FAKES_OVERLAY}, as compose project ${RELEASE_PROJECT}. Production-mode
               Keycloak with no users; harness user create (and so harness mobile flow)
               makes accounts through its admin API. Uses the dev stack's ports, so stop
               that first (harness down). Refuses a tag whose realm has no mimos-mobile.
  down         remove that stack and its volumes
${RUN_USAGE}

Then: harness mobile up --run latest, and harness mobile flow <name> --run latest.
CI's android-skew job does the same on main (docs/harness/releasing.md).`,
  async run(argv) {
    const args = parseReleaseArgs(argv);
    return args.action === "up" ? up(args, argv) : down();
  },
};
