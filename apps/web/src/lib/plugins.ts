/**
 * A plugin's homepage as a link target, or null. The API already drops
 * anything but http(s) from manifests (ADR-0013); this keeps the browser
 * safe on its own, since the value comes from a plugin.
 */
export function pluginHomepage(url: string | undefined): string | null {
  if (!url) {
    return null;
  }
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.href : null;
  } catch {
    return null;
  }
}
