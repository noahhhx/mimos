import type { PictureResult } from "./recipe-pictures.ts";

/** The problem detail's own message, or `fallback` when the API gave none. */
export function problemDetail(result: { error?: unknown }, fallback: string): string {
  const error = result.error as { detail?: string } | undefined;
  return error?.detail ?? fallback;
}

/** Turns a client call into a PictureResult, so a thrown network error stops a save like a rejected call. */
export async function apiCall<T>(
  call: () => Promise<{ data?: T; error?: unknown; response?: Response }>,
  fallback: string,
): Promise<PictureResult<T>> {
  try {
    const result = await call();
    if (result.error === undefined && result.response?.ok) {
      return { ok: true, value: result.data as T };
    }
    return { ok: false, status: result.response?.status, message: problemDetail(result, fallback) };
  } catch {
    return { ok: false, message: fallback };
  }
}
