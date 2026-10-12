import type { RecipeDetail, RecipePicture } from "@mimos/api-client";

/** Mirrors RecipePictureService.MAX_PICTURES; the API is the authority. */
export const MAX_PICTURES = 8;
/** Mirrors RecipePictureService.MAX_PICTURE_BYTES. */
export const MAX_PICTURE_BYTES = 2 * 1024 * 1024;
/** The longest side a picture is shrunk to before it is uploaded. */
export const LONG_EDGE = 1600;
/** JPEG qualities tried in turn until a shrunk picture fits MAX_PICTURE_BYTES. */
export const QUALITY_STEPS = [0.85, 0.75, 0.6, 0.45] as const;

/** A picture shrunk in the browser and not uploaded yet. */
export type PreparedPicture = { blob: Blob };

export type PictureSlot =
  | { kind: "saved"; picture: RecipePicture }
  | { kind: "new"; key: string; prepared: PreparedPicture };

/**
 * What the recipe form shows (slots, in order) and the saved pictures the
 * cook took away. Uploads append on the server, so new slots always follow
 * saved ones: the draft never reorders, and slot order is server order.
 */
export type PictureDraft = { slots: PictureSlot[]; removed: string[] };

/** The size that fits within `longEdge` on both sides, keeping the ratio; never larger than the original. */
export function fitWithin(width: number, height: number, longEdge = LONG_EDGE): { width: number; height: number } {
  const scale = Math.min(1, longEdge / Math.max(width, height));
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

export function draftOf(recipe?: Pick<RecipeDetail, "pictures">): PictureDraft {
  return { slots: (recipe?.pictures ?? []).map((picture) => ({ kind: "saved", picture })), removed: [] };
}

/** Appends picks after every slot, keeping at most MAX_PICTURES; says how many did not fit. */
export function addPicks(
  draft: PictureDraft,
  picks: { key: string; prepared: PreparedPicture }[],
): { draft: PictureDraft; leftOut: number } {
  const room = Math.max(0, MAX_PICTURES - draft.slots.length);
  const added = picks.slice(0, room).map((pick): PictureSlot => ({ kind: "new", ...pick }));
  return { draft: { ...draft, slots: [...draft.slots, ...added] }, leftOut: picks.length - added.length };
}

/** Takes the slot at `index` out: a saved picture is remembered for removal, a new one is just dropped. */
export function removeSlot(draft: PictureDraft, index: number): PictureDraft {
  const slot = draft.slots[index];
  if (!slot) {
    return draft;
  }
  return {
    slots: draft.slots.filter((_, i) => i !== index),
    removed: slot.kind === "saved" ? [...draft.removed, slot.picture.id] : draft.removed,
  };
}

/** What Save still has to send: removals first, which frees room under the limit, then uploads in slot order. */
export function pendingChanges(draft: PictureDraft): {
  remove: string[];
  upload: { key: string; prepared: PreparedPicture }[];
} {
  return {
    remove: draft.removed,
    upload: draft.slots.flatMap((slot) => (slot.kind === "new" ? [{ key: slot.key, prepared: slot.prepared }] : [])),
  };
}

export function hasChanges(draft: PictureDraft): boolean {
  const pending = pendingChanges(draft);
  return pending.remove.length > 0 || pending.upload.length > 0;
}

/** The draft once a removal reached the server, or found the picture already gone. */
export function afterRemoved(draft: PictureDraft, pictureId: string): PictureDraft {
  return { ...draft, removed: draft.removed.filter((id) => id !== pictureId) };
}

/** The draft once an upload reached the server: the new slot becomes that saved picture, in place. */
export function afterUploaded(draft: PictureDraft, key: string, picture: RecipePicture): PictureDraft {
  return {
    ...draft,
    slots: draft.slots.map((slot) => (slot.kind === "new" && slot.key === key ? { kind: "saved", picture } : slot)),
  };
}

export type PictureResult<T> = { ok: true; value: T } | { ok: false; status?: number; message: string };

/** The calls Save makes, injected so the save loop runs without a browser or a server. */
export type PictureApi = {
  remove: (recipeId: string, pictureId: string) => Promise<PictureResult<void>>;
  upload: (recipeId: string, blob: Blob) => Promise<PictureResult<RecipePicture>>;
};

/**
 * Sends what the draft still holds to the saved recipe, one call at a time,
 * and returns the draft as far as the server has it. A failure stops there
 * with the API's message, so saving again sends only the rest. A removal
 * that finds the picture gone counts as done.
 */
export async function syncPictures(
  api: PictureApi,
  recipeId: string,
  start: PictureDraft,
): Promise<{ draft: PictureDraft; error?: string }> {
  let draft = start;
  for (const pictureId of pendingChanges(draft).remove) {
    const result = await api.remove(recipeId, pictureId);
    if (!result.ok && result.status !== 404) {
      return { draft, error: result.message };
    }
    draft = afterRemoved(draft, pictureId);
  }
  for (const pick of pendingChanges(draft).upload) {
    const result = await api.upload(recipeId, pick.prepared.blob);
    if (!result.ok) {
      return { draft, error: result.message };
    }
    draft = afterUploaded(draft, pick.key, result.value);
  }
  return { draft };
}
