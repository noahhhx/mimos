/**
 * Redaction applied to everything written to a run folder (principle 5):
 * credentials never land on disk, even in a gitignored folder, because run
 * folders get pasted into issues and uploaded as CI artifacts.
 */

export const REDACTED = "[REDACTED]";

const SECRET_HEADERS = new Set(["authorization", "proxy-authorization", "cookie", "set-cookie"]);
const SECRET_FIELDS = new Set(["access_token", "refresh_token", "id_token", "password", "client_secret"]);

/** Header names keep their case; an auth scheme (`Bearer`) is kept so the request shape stays readable. */
export function redactHeaders(headers: Readonly<Record<string, string>>): Record<string, string> {
  const result: Record<string, string> = {};
  for (const [name, value] of Object.entries(headers)) {
    if (!SECRET_HEADERS.has(name.toLowerCase())) {
      result[name] = redactText(value);
    } else {
      const scheme = /^(Bearer|Basic)\s+/i.exec(value)?.[1];
      result[name] = scheme ? `${scheme} ${REDACTED}` : REDACTED;
    }
  }
  return result;
}

/** Recursively replaces secret fields (by name, case-insensitive) and token-shaped strings. */
export function redactJson(value: unknown): unknown {
  if (typeof value === "string") {
    return redactText(value);
  }
  if (Array.isArray(value)) {
    return value.map(redactJson);
  }
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, field]) => [
        key,
        SECRET_FIELDS.has(key.toLowerCase()) ? REDACTED : redactJson(field),
      ]),
    );
  }
  return value;
}

const JWT = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g;
const BEARER = /\b(Bearer)\s+[A-Za-z0-9._~+/-]+=*/gi;

/** Free text (logs, unparseable bodies): JWTs and bearer credentials wherever they appear. */
export function redactText(text: string): string {
  return text.replace(BEARER, `$1 ${REDACTED}`).replace(JWT, REDACTED);
}

function redactHeaderValue(name: string, value: string): string {
  return redactHeaders({ [name]: value })[name] ?? REDACTED;
}

/**
 * HAR-shaped data — a browser HAR, or the network records in a Playwright
 * trace: `{name, value}` pairs are headers or form params (redacted by name),
 * every cookie value goes, `{mimeType, text}` bodies are redacted as bodies,
 * and everything else as for redactJson.
 */
export function redactHar(value: unknown, key = ""): unknown {
  if (typeof value === "string") {
    return redactText(value);
  }
  if (Array.isArray(value)) {
    return key === "cookies"
      ? value.map((cookie: unknown) =>
          cookie !== null && typeof cookie === "object" ? { ...cookie, value: REDACTED } : REDACTED,
        )
      : value.map((item) => redactHar(item));
  }
  if (value === null || typeof value !== "object") {
    return value;
  }
  const record = value as Record<string, unknown>;
  const result: Record<string, unknown> = {};
  for (const [field, item] of Object.entries(record)) {
    result[field] = SECRET_FIELDS.has(field.toLowerCase()) ? REDACTED : redactHar(item, field);
  }
  if (typeof record.name === "string" && typeof record.value === "string") {
    const name = record.name.toLowerCase();
    if (SECRET_HEADERS.has(name)) result.value = redactHeaderValue(record.name, record.value);
    else if (SECRET_FIELDS.has(name)) result.value = REDACTED;
  }
  if (typeof record.text === "string" && typeof record.mimeType === "string" && record.encoding !== "base64") {
    result.text = redactBody(record.text, record.mimeType);
  }
  return result;
}

/** A request/response body: JSON and form bodies by field name, anything else as free text. */
export function redactBody(body: string, contentType = ""): string {
  const mediaType = contentType.split(";")[0]?.trim().toLowerCase() ?? "";
  if (mediaType === "application/x-www-form-urlencoded") {
    const params = new URLSearchParams(body);
    for (const key of [...params.keys()]) {
      if (SECRET_FIELDS.has(key.toLowerCase())) {
        params.set(key, REDACTED);
      }
    }
    return redactText(params.toString());
  }
  if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
    try {
      const parsed: unknown = JSON.parse(body);
      const redacted = JSON.stringify(redactJson(parsed));
      // Keep the original bytes (formatting included) unless something was replaced.
      return redacted === JSON.stringify(parsed) ? body : redacted;
    } catch {
      // Not JSON after all (or malformed on purpose) — fall through to text.
    }
  }
  return redactText(body);
}
