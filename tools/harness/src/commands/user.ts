import { parseCommandArgs, UsageError } from "../args.ts";
import type { Command } from "../command.ts";
import { createUser, envLines } from "../accounts.ts";
import { FRESH_PASSWORD } from "../config.ts";

export function userEnv(username: string): Record<string, string> {
  return { MIMOS_USER: username, MIMOS_PASSWORD: FRESH_PASSWORD };
}

export function parseUserArgs(argv: string[]): "create" {
  const { positionals } = parseCommandArgs({ args: argv, allowPositionals: true, options: {} });
  const [action, ...extra] = positionals;
  if (action !== "create" || extra.length > 0) throw new UsageError("expected: create");
  return action;
}

export const userCommand: Command = {
  name: "user",
  summary: "Create a fresh Keycloak user for a flow",
  usage: `harness user create

Creates a realm user through Keycloak's admin API, as ApiIntegrationTestSupport.createUser()
does, and prints what a Maestro flow needs on stdout:

  MIMOS_USER=user-1a2b3c4d
  MIMOS_PASSWORD=${FRESH_PASSWORD}

  maestro test -e MIMOS_USER=... -e MIMOS_PASSWORD=... flow.yaml

The account can sign in on the web and works with --as in token, api, and ui. Its household
of one is made on its first request. Needs the stack up (harness up); the admin login is
KEYCLOAK_ADMIN_USERNAME and KEYCLOAK_ADMIN_PASSWORD, as for compose (admin and admin).`,
  async run(argv) {
    parseUserArgs(argv);
    process.stdout.write(`${envLines(userEnv(await createUser()))}\n`);
    return 0;
  },
};
