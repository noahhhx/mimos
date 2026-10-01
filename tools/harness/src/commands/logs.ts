import { displayCommand, parseCommandArgs } from "../args.ts";
import { finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs } from "../compose.ts";
import { appendSummary } from "../run.ts";

/** Plain log capture into a run folder; filtering by request ID arrives in step 3. */

export function parseLogsArgs(argv: string[]): { services: string[]; since: string | undefined; run: string | undefined } {
  const { values } = parseCommandArgs({
    args: argv,
    options: { service: { type: "string", multiple: true }, since: { type: "string" }, ...RUN_OPTION },
  });
  return { services: values.service ?? [], since: values.since, run: values.run };
}

export const logsCommand: Command = {
  name: "logs",
  summary: "Capture service logs into a run folder",
  usage: `harness logs [--service <s>]... [--since <t>]

  --service <s>   only this compose service (repeatable; default: all)
  --since <t>     a duration (10m) or timestamp (2026-10-01T15:04:12Z), as for docker compose logs
${RUN_USAGE}

Writes logs/<service>.log (timestamped, tokens redacted).`,
  async run(argv) {
    const args = parseLogsArgs(argv);
    const startedAt = new Date();
    const run = startRun("logs", startedAt, args.run);
    const logs = await captureLogs(run, { services: args.services, since: args.since });
    const listing = logs.map((log) => `- \`${log.file}\` — ${log.lines} lines`);
    appendSummary(
      run,
      [`## \`${displayCommand(["logs", ...argv])}\``, "", args.since ? `Since ${args.since}.` : "Full history.", "", ...listing].join("\n"),
    );
    await finishRun(run, ["logs", ...argv], startedAt, 0);
    process.stdout.write(`${listing.join("\n")}\n`);
    return 0;
  },
};
