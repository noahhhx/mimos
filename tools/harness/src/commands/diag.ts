import { relative } from "node:path";

import { actuator, discover, routes, routeTable, unhealthyComponents } from "../actuator.ts";
import { displayCommand, parseCommandArgs } from "../args.ts";
import { finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs, compose, describeLogs, gitState, isHealthy, ps } from "../compose.ts";
import { BUILD_INPUTS, DEFAULT_USER, endpoints, MIGRATIONS_DIR, REALM } from "../config.ts";
import { send } from "../http.ts";
import { migrationReport, MIGRATION_HISTORY, migrationScripts, parseCsv, sql } from "../postgres.ts";
import { redactConfig, redactJson, redactText } from "../redact.ts";
import { appendSummary, writeRunFile, type Run } from "../run.ts";
import { serviceReports } from "./status.ts";
import { fetchToken } from "./token.ts";

/**
 * A diagnostics snapshot of the running stack in diag/: compose state and
 * image freshness, the API's actuator endpoints (what the debug overlay
 * exposes), applied migrations, plugin manifests, Keycloak's realm metadata,
 * and recent logs. Each part is independent; one that cannot be reached is
 * reported as skipped and the rest still land.
 */

/** Actuator endpoints snapshotted as JSON, in summary order; threaddump is fetched as text. */
const JSON_ENDPOINTS = ["health", "info", "mappings", "env", "configprops", "loggers", "flyway"] as const;

/** Endpoints whose values may name secrets (shown by the debug overlay), redacted by key. */
const CONFIG_ENDPOINTS = new Set(["env", "configprops"]);

/** What one part of the snapshot contributes to summary.md. */
interface Part {
  /** One summary bullet. */
  bullet: string;
  /** Things wrong with the stack — any one makes the exit code 1. */
  problems: string[];
  /** What could not be captured, and why. */
  skipped: string[];
}

export function parseDiagArgs(argv: string[]): { since: string; run: string | undefined } {
  const { values } = parseCommandArgs({ args: argv, options: { since: { type: "string" }, ...RUN_OPTION } });
  return { since: values.since ?? "15m", run: values.run };
}

function json(value: unknown): string {
  return `${JSON.stringify(value, null, 2)}\n`;
}

async function stackPart(run: Run, now: Date): Promise<Part> {
  const [{ reports, notes }, states, git] = await Promise.all([serviceReports(now), ps(), gitState()]);
  writeRunFile(run, "diag/compose.json", json({ git, services: reports, containers: states }));
  const unhealthy = states.filter((state) => !isHealthy(state));
  return {
    bullet:
      `- **Stack:** ${states.length - unhealthy.length}/${states.length} services healthy · ` +
      `git ${git.sha?.slice(0, 12) ?? "unknown"}${git.dirty ? " (dirty)" : ""} (\`diag/compose.json\`)`,
    problems: [
      ...unhealthy.map((state) => `${state.service} is ${state.state}${state.health ? ` (${state.health})` : ""}`),
      ...reports.filter((report) => report.state === "absent").map((report) => `${report.service} is not running`),
      ...notes,
    ],
    skipped: [],
  };
}

async function apiPart(run: Run): Promise<Part> {
  const problems: string[] = [];
  const skipped: string[] = [];
  const health = await send("GET", new URL("/actuator/health", endpoints().api), { Accept: "application/json" });
  writeRunFile(run, "diag/actuator/health.json", json(redactJson(JSON.parse(health.body))));
  const status = (JSON.parse(health.body) as { status?: string }).status ?? `HTTP ${health.status}`;
  if (status !== "UP") problems.push(`API health is ${status}: ${unhealthyComponents(JSON.parse(health.body)).join(", ") || "no component details"}`);

  const token = await fetchToken(DEFAULT_USER).catch((error: Error) => error);
  if (token instanceof Error) {
    skipped.push(`every actuator endpoint but health — no token: ${token.message}`);
    return { bullet: `- **API:** health ${status} (\`diag/actuator/health.json\`)`, problems, skipped };
  }
  const exposed = await discover(token);
  const captured = ["health"];
  let routeCount: number | undefined;
  for (const name of JSON_ENDPOINTS.filter((endpoint) => endpoint !== "health")) {
    if (!exposed.includes(name)) continue;
    const response = await actuator(`/${name}`, token);
    if (response.status !== 200) {
      skipped.push(`actuator ${name}: ${response.status} ${response.statusText}`);
      continue;
    }
    const body: unknown = JSON.parse(response.body);
    writeRunFile(run, `diag/actuator/${name}.json`, json(CONFIG_ENDPOINTS.has(name) ? redactConfig(body) : redactJson(body)));
    captured.push(name);
    if (name === "mappings") {
      const list = routes(body);
      routeCount = list.length;
      writeRunFile(run, "diag/routes.txt", routeTable(list));
    }
  }
  if (exposed.includes("threaddump")) {
    const response = await actuator("/threaddump", token, { accept: "text/plain" });
    if (response.status === 200) {
      writeRunFile(run, "diag/actuator/threaddump.txt", redactText(response.body));
      captured.push("threaddump");
    } else {
      skipped.push(`actuator threaddump: ${response.status} ${response.statusText}`);
    }
  }
  const missing = [...JSON_ENDPOINTS, "threaddump"].filter((name) => !exposed.includes(name));
  if (missing.length > 0) {
    skipped.push(`actuator ${missing.join(", ")} — not exposed; start the stack with \`harness up --debug\` for them`);
  }
  return {
    bullet:
      `- **API:** health ${status} · actuator: ${captured.join(", ")} (\`diag/actuator/\`)` +
      (routeCount === undefined ? "" : ` · ${routeCount} routes with their consumes/produces (\`diag/routes.txt\`)`),
    problems,
    skipped,
  };
}

async function migrationsPart(run: Run): Promise<Part> {
  const result = await sql(MIGRATION_HISTORY, { csv: true });
  if (result.code !== 0) {
    return { bullet: "- **Migrations:** not read", problems: [], skipped: [`migrations — psql failed: ${result.stderr.trim()}`] };
  }
  writeRunFile(run, "diag/migrations.csv", result.stdout);
  const report = migrationReport(parseCsv(result.stdout), migrationScripts());
  const problems = [
    ...report.failed.map((script) => `migration ${script} failed (flyway_schema_history.success = false)`),
    ...report.pending.map((script) => `migration ${script} is in ${MIGRATIONS_DIR} but not applied — rebuild and restart the API (harness up)`),
  ];
  return {
    bullet: `- **Migrations:** ${report.applied} applied, ${report.failed.length} failed, ${report.pending.length} pending vs. the repo (\`diag/migrations.csv\`)`,
    problems,
    skipped: [],
  };
}

/** Plugin sidecars are not published to the host; their manifests are read from inside each container. */
async function pluginsPart(run: Run): Promise<Part> {
  const running = new Set((await ps()).filter(isHealthy).map((state) => state.service));
  const plugins = Object.entries(BUILD_INPUTS)
    .filter(([, inputs]) => inputs.dockerfile.startsWith("plugins/"))
    .map(([service]) => service);
  const files: string[] = [];
  const skipped: string[] = [];
  for (const service of plugins) {
    if (!running.has(service)) {
      skipped.push(`${service} manifest — the service is not running and healthy`);
      continue;
    }
    const result = await compose(["exec", "-T", service, "wget", "-qO-", "http://127.0.0.1:8080/manifest"]);
    if (result.code !== 0) {
      skipped.push(`${service} manifest — ${result.stderr.trim() || `wget exited ${result.code}`}`);
      continue;
    }
    const file = `diag/plugins/${service}-manifest.json`;
    writeRunFile(run, file, json(redactJson(JSON.parse(result.stdout))));
    files.push(`\`${file}\``);
  }
  return { bullet: `- **Plugins:** ${files.length ? `manifests ${files.join(", ")}` : "no manifest captured"}`, problems: [], skipped };
}

async function keycloakPart(run: Run): Promise<Part> {
  const base = `${endpoints().keycloak}/realms/${REALM}`;
  const files: string[] = [];
  const skipped: string[] = [];
  for (const [path, file] of [
    ["", "diag/keycloak/realm.json"],
    ["/.well-known/openid-configuration", "diag/keycloak/openid-configuration.json"],
  ] as const) {
    const response = await send("GET", new URL(`${base}${path}`), { Accept: "application/json" });
    if (response.status !== 200) {
      skipped.push(`keycloak ${base}${path}: ${response.status} ${response.statusText}`);
      continue;
    }
    writeRunFile(run, file, json(redactJson(JSON.parse(response.body))));
    files.push(`\`${file}\``);
  }
  return { bullet: `- **Keycloak:** realm ${REALM}${files.length ? ` — ${files.join(", ")}` : " not reachable"}`, problems: [], skipped };
}

async function logsPart(run: Run, since: string): Promise<Part> {
  return { bullet: `- **Logs (last ${since}):** ${describeLogs(await captureLogs(run, { since }))}`, problems: [], skipped: [] };
}

/** A part that threw (say, the API is down) becomes a skipped entry instead of failing the snapshot. */
async function guarded(name: string, part: () => Promise<Part>): Promise<Part> {
  try {
    return await part();
  } catch (error) {
    const message = (error as Error).message.split("\n")[0] ?? "";
    return { bullet: `- **${name}:** not captured`, problems: [], skipped: [`${name.toLowerCase()} — ${message}`] };
  }
}

export const diagCommand: Command = {
  name: "diag",
  summary: "Snapshot the running stack: state, actuator, routes, migrations, plugins, logs",
  usage: `harness diag [--since <t>]

  --since <t>   how much recent log to capture (default 15m), as for docker compose logs
${RUN_USAGE}

Writes diag/ in the run folder: compose state and image freshness, the API's
actuator health/info/mappings/env/configprops/loggers/flyway/threaddump (all but
health need the debug overlay: harness up --debug), routes.txt (every route with
the media types it consumes and produces), applied migrations vs. the repo, plugin
manifests, Keycloak realm metadata; and logs/. Secrets are redacted. summary.md
lists problems and what was skipped. Exit code: 0 when no problem was found, 1 otherwise.`,
  async run(argv) {
    const args = parseDiagArgs(argv);
    const startedAt = new Date();
    const run = startRun("diag", startedAt, args.run);
    const parts = await Promise.all([
      guarded("Stack", () => stackPart(run, startedAt)),
      guarded("API", () => apiPart(run)),
      guarded("Migrations", () => migrationsPart(run)),
      guarded("Plugins", () => pluginsPart(run)),
      guarded("Keycloak", () => keycloakPart(run)),
      guarded("Logs", () => logsPart(run, args.since)),
    ]);
    const problems = parts.flatMap((part) => part.problems);
    const skipped = parts.flatMap((part) => part.skipped);

    const section = [
      `## \`${displayCommand(["diag", ...argv])}\` — ${problems.length ? `${problems.length} problem${problems.length === 1 ? "" : "s"}` : "no problems"}`,
      "",
      ...parts.map((part) => part.bullet),
    ];
    if (problems.length) section.push("", "**Problems:**", "", ...problems.map((problem) => `- ${problem}`));
    if (skipped.length) section.push("", "**Skipped:**", "", ...skipped.map((entry) => `- ${entry}`));
    appendSummary(run, section.join("\n"));
    const exitCode = problems.length ? 1 : 0;
    await finishRun(run, ["diag", ...argv], startedAt, exitCode);

    const report = [
      ...parts.map((part) => part.bullet.replace(/^- \*\*(.+?):\*\*/, "$1:").replaceAll("`", "")),
      ...problems.map((problem) => `problem: ${problem}`),
      ...skipped.map((entry) => `skipped: ${entry}`),
      `summary: ${relative(process.cwd(), `${run.dir}/summary.md`)}`,
    ];
    process.stdout.write(`${report.join("\n")}\n`);
    return exitCode;
  },
};
