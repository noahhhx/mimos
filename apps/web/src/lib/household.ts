/** Names as a sentence lists them: "Sam", "Sam and Alex", "Sam, Alex and Jo". */
export function listNames(names: readonly string[]): string {
  if (names.length <= 1) {
    return names[0] ?? "";
  }
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

/** A household as its members' names: "Sam and Alex's household". */
export function householdName(names: readonly string[]): string {
  return names.length === 0 ? "this household" : `${listNames(names)}'s household`;
}

/** The link that opens an invite on this Mimos. */
export function inviteLink(appUrl: string, token: string): string {
  return `${appUrl.replace(/\/+$/, "")}/app/join/${encodeURIComponent(token)}`;
}
