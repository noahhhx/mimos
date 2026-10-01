/**
 * Request IDs tie a call to the API's log lines: the API echoes a
 * well-formed `X-Request-Id` and puts it on every line the request logs
 * (docs/harness/step-3-logs.md).
 */
export const REQUEST_ID_HEADER = "X-Request-Id";

export function newRequestId(): string {
  // randomUUID exists only in secure contexts; a self-hosted instance served
  // over plain http on a LAN address is not one.
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
