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

/** The Android app's own scheme for links it opens (ADR-0029), apart from its sign-in redirect. */
export const APP_LINK_SCHEME = "io.github.noahhhx.mimos";

/** The link that opens an invite in the Android app, naming this Mimos so the app can connect to it. */
export function appJoinLink(appUrl: string, token: string): string {
  const instance = encodeURIComponent(appUrl.replace(/\/+$/, ""));
  return `${APP_LINK_SCHEME}://join?instance=${instance}&token=${encodeURIComponent(token)}`;
}
