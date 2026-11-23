import type { PictureResult } from "./recipe-pictures.ts";

export function problemDetail(result: { error?: unknown }, fallback: string): string {
  const error = result.error as { detail?: string } | undefined;
  return error?.detail ?? fallback;
}

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
