import { displayCommand, HarnessError, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs, compose, composeEnv, isHealthy, ps, type ServiceState } from "../compose.ts";
import { COMPOSE_FILE, composeProject, DEBUG_OVERLAY } from "../config.ts";
import { exec } from "../exec.ts";
import { redactText } from "../redact.ts";
import { appendSummary, writeRunFile } from "../run.ts";

/** Stack lifecycle: up (always on freshly built images), down, reset. */

export function parseUpArgs(argv: string[]): { build: boolean; debug: boolean; sqlLog: boolean; run: string | undefined } {
  const { values } = parseCommandArgs({
    args: argv,
    options: { "no-build": { type: "boolean" }, debug: { type: "boolean" }, "sql-log": { type: "boolean" }, ...RUN_OPTION },
  });
  if (values["sql-log"] && !values.debug) {
    throw new UsageError("--sql-log needs --debug (the overlay sets Postgres's statement logging)");
  }
  return { build: !values["no-build"], debug: values.debug ?? false, sqlLog: values["sql-log"] ?? false, run: values.run };
}

/** compose.debug.yml reads this; anything but 0 (unset: -1) leaves statement logging off. */
export function upEnv(sqlLog: boolean): NodeJS.ProcessEnv {
  const { POSTGRES_LOG_MIN_DURATION_STATEMENT: _ignored, ...env } = composeEnv();
  return sqlLog ? { ...env, POSTGRES_LOG_MIN_DURATION_STATEMENT: "0" } : env;
}

function stateTable(states: readonly ServiceState[]): string {
  const rows = states.map(
    (s) => `| ${s.service} | ${s.state}${s.state === "exited" ? ` (${s.exitCode})` : ""} | ${s.health || "—"} |`,
  );
  return ["| Service | State | Health |", "| --- | --- | --- |", ...rows].join("\n");
}

function tail(text: string, lines: number): string {
  return text.trimEnd().split("\n").slice(-lines).join("\n");
}

export const upCommand: Command = {
  name: "up",
  summary: "Build images and start the stack, waiting until healthy",
  usage: `harness up [--no-build] [--debug [--sql-log]]

  --no-build   skip \`docker compose build\` (images may be stale — see harness status)
  --debug      add the debug overlay ${DEBUG_OVERLAY} (JSON API logs, actuator
               diagnostics for harness diag and harness loglevel; see docs/harness)
  --sql-log    with --debug: Postgres logs every statement and its duration
${RUN_USAGE}

Runs \`docker compose build\` then \`up -d --wait\` on ${COMPOSE_FILE}. Output is
kept in compose/; when the stack does not become healthy, every service's logs
are captured and the unhealthy ones are summarized in summary.md.`,
  async run(argv) {
    const args = parseUpArgs(argv);
    const startedAt = new Date();
    const run = startRun("up", startedAt, args.run);
    const section = [`## \`${displayCommand(["up", ...argv])}\``, ""];
    const finish = async (exitCode: number): Promise<number> => {
      appendSummary(run, section.join("\n"));
      await finishRun(run, ["up", ...argv], startedAt, exitCode);
      return exitCode;
    };

    if (args.build) {
      const build = await compose(["build"], { debug: args.debug, stream: true, env: upEnv(args.sqlLog) });
      writeRunFile(run, "compose/build.log", redactText(build.stdout + build.stderr));
      if (build.code !== 0) {
        section.push(
          `**Build failed** (exit ${build.code}) — full output in \`compose/build.log\`. Last lines:`,
          "",
          fenced(tail(build.stderr || build.stdout, 40)),
        );
        process.stderr.write("harness: build failed — see compose/build.log in the run folder\n");
        return finish(1);
      }
      section.push("- Images built (`compose/build.log`).");
    } else {
      section.push("- Build skipped (`--no-build`).");
    }

    const up = await compose(["up", "-d", "--wait"], { debug: args.debug, stream: true, env: upEnv(args.sqlLog) });
    writeRunFile(run, "compose/up.log", redactText(up.stdout + up.stderr));
    const states = await ps();
    writeRunFile(run, "compose/ps.json", `${JSON.stringify(states, null, 2)}\n`);
    section.push(`- \`compose up --wait\` exited ${up.code} (\`compose/up.log\`).`, "", stateTable(states));

    const unhealthy = states.filter((state) => !isHealthy(state));
    if (up.code === 0 && unhealthy.length === 0) {
      process.stderr.write("harness: stack is up and healthy\n");
      return finish(0);
    }

    const logs = await captureLogs(run);
    section.push("", `**Stack is not healthy.** Logs of every service are in \`logs/\`.`);
    for (const state of unhealthy) {
      const text = logs.find((captured) => captured.service === state.service)?.text ?? "";
      section.push("", `### ${state.service} — ${state.state} ${state.health}`.trimEnd(), "", fenced(tail(text, 30)));
    }
    process.stderr.write(
      `harness: stack is not healthy (${unhealthy.map((s) => s.service).join(", ") || "compose up failed"}) — see summary.md\n`,
    );
    return finish(1);
  },
};

export const downCommand: Command = {
  name: "down",
  summary: "Stop and remove the stack's containers (data volumes are kept)",
  usage: "harness down\n\nRuns `docker compose down`. The database volume survives; use harness reset to wipe it.",
  async run(argv) {
    parseCommandArgs({ args: argv, options: {} });
    const result = await compose(["down"], { stream: true });
    return result.code === 0 ? 0 : 1;
  },
};

export const resetCommand: Command = {
  name: "reset",
  summary: "Stop the stack and delete its volumes (wipes the database)",
  usage:
    "harness reset\n\nRuns `docker compose down -v --remove-orphans`: containers, the network, and every\n" +
    "volume of the compose project — the database included. Prints the volumes first.",
  async run(argv) {
    parseCommandArgs({ args: argv, options: {} });
    const volumes = await exec("docker", [
      "volume",
      "ls",
      "--quiet",
      "--filter",
      `label=com.docker.compose.project=${composeProject()}`,
    ]);
    if (volumes.code !== 0) {
      throw new HarnessError(`docker volume ls failed: ${volumes.stderr.trim()}`);
    }
    const names = volumes.stdout.split("\n").filter(Boolean);
    process.stderr.write(
      names.length
        ? `harness: removing the stack's containers and volumes: ${names.join(", ")}\n`
        : "harness: removing the stack's containers (no volumes exist)\n",
    );
    const result = await compose(["down", "-v", "--remove-orphans"], { stream: true });
    return result.code === 0 ? 0 : 1;
  },
};
