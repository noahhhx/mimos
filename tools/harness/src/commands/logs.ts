import { displayCommand, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs } from "../compose.ts";
import { describeRequestLines, REQUEST_ID, requestLines } from "../logs.ts";
import { appendSummary, writeRunFile } from "../run.ts";

/** Log capture into a run folder, optionally narrowed to one request's lines across services. */

export interface LogsArgs {
  services: string[];
  since: string | undefined;
  requestId: string | undefined;
  run: string | undefined;
}

export function parseLogsArgs(argv: string[]): LogsArgs {
  const { values } = parseCommandArgs({
    args: argv,
    options: {
      service: { type: "string", multiple: true },
      since: { type: "string" },
      "request-id": { type: "string" },
      ...RUN_OPTION,
    },
  });
  const requestId = values["request-id"];
  if (requestId !== undefined && !REQUEST_ID.test(requestId)) {
    throw new UsageError(`--request-id: not a request ID (1-64 of A-Z a-z 0-9 . _ -): ${requestId}`);
  }
  return { services: values.service ?? [], since: values.since, requestId, run: values.run };
}

export const logsCommand: Command = {
  name: "logs",
  summary: "Capture service logs into a run folder, or one request's lines",
  usage: `harness logs [--service <s>]... [--since <t>] [--request-id <id>]

  --service <s>       only this compose service (repeatable; default: all)
  --since <t>         a duration (10m) or timestamp (2026-10-01T15:04:12Z), as for docker compose logs
  --request-id <id>   only the lines this request produced, across services (the X-Request-Id
                      the API echoes; harness api and harness ui summaries list them)
${RUN_USAGE}

Writes logs/<service>.log (timestamped, tokens redacted). With --request-id, also
writes logs/request-<id>.log, prints the matching lines, and exits 1 when none match.`,
  async run(argv) {
    const args = parseLogsArgs(argv);
    const startedAt = new Date();
    const run = startRun("logs", startedAt, args.run);
    const logs = await captureLogs(run, { services: args.services, since: args.since });
    const listing = logs.map((log) => `- \`${log.file}\` — ${log.lines} lines`);
    const section = [`## \`${displayCommand(["logs", ...argv])}\``, "", args.since ? `Since ${args.since}.` : "Full history.", "", ...listing];
    let exitCode = 0;
    let output = listing;

    if (args.requestId !== undefined) {
      const matches = requestLines(logs, args.requestId);
      const file = `logs/request-${args.requestId}.log`;
      writeRunFile(run, file, matches.flatMap((match) => match.lines.map((line) => `${match.service} | ${line}`)).join("\n") + "\n");
      const lines = describeRequestLines(matches);
      section.push(
        "",
        lines.length > 0
          ? `Lines for request \`${args.requestId}\` (raw in \`${file}\`):`
          : `No lines mention request \`${args.requestId}\`. A request the API never received (CORS, network) logs nothing; an API image from before harness step 3 logs no IDs — see \`harness status\`.`,
      );
      if (lines.length > 0) section.push("", fenced(lines.join("\n")));
      output = lines.length > 0 ? lines : [`no log lines mention request ${args.requestId}`];
      exitCode = lines.length > 0 ? 0 : 1;
    }

    appendSummary(run, section.join("\n"));
    await finishRun(run, ["logs", ...argv], startedAt, exitCode);
    process.stdout.write(`${output.join("\n")}\n`);
    return exitCode;
  },
};
