import { parseCommandArgs, UsageError } from "../args.ts";
import type { Command } from "../command.ts";
import { envLines, joinHousehold } from "../accounts.ts";
import { passwordFor, fetchToken } from "./token.ts";

export interface HouseholdArgs {
  owner: string;
  member: string;
}

export function householdEnv({ owner, member }: HouseholdArgs): Record<string, string> {
  return {
    MIMOS_OWNER: owner,
    MIMOS_OWNER_PASSWORD: passwordFor(owner),
    MIMOS_MEMBER: member,
    MIMOS_MEMBER_PASSWORD: passwordFor(member),
  };
}

export function parseHouseholdArgs(argv: string[]): HouseholdArgs {
  const { positionals } = parseCommandArgs({ args: argv, allowPositionals: true, options: {} });
  const [action, owner, member, ...extra] = positionals;
  if (action !== "join" || owner === undefined || member === undefined || extra.length > 0) {
    throw new UsageError("expected: join <owner> <member>");
  }
  if (owner === member) throw new UsageError("the owner and the member must be different users");
  passwordFor(owner);
  passwordFor(member);
  return { owner, member };
}

export const householdCommand: Command = {
  name: "household",
  summary: "Put two users in one household (invite and join through the API)",
  usage: `harness household join <owner> <member>

<owner> invites and <member> joins, both through /api/v1/household. Each is a realm test user
(test, test2) or an account from \`harness user create\`. The member leaves its own household
of one: its plans, shopping lists, and plugin choices go, its recipes move to the owner's.
Prints what a flow needs on stdout:

  MIMOS_OWNER=... MIMOS_OWNER_PASSWORD=... MIMOS_MEMBER=... MIMOS_MEMBER_PASSWORD=...

The invite token is never printed or recorded. Needs the stack up (harness up).`,
  async run(argv) {
    const args = parseHouseholdArgs(argv);
    const env = householdEnv(args);
    const [ownerToken, memberToken] = await Promise.all([fetchToken(args.owner), fetchToken(args.member)]);
    await joinHousehold(ownerToken, memberToken);
    process.stdout.write(`${envLines(env)}\n`);
    return 0;
  },
};
