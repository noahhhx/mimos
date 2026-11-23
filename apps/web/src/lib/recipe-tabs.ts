import type { RecipeCollection, RecipeCollectionInput } from "@mimos/api-client";

import type { PictureResult, PreparedPicture } from "./recipe-pictures.ts";

export type RecipeTab =
  | { kind: "gallery" }
  | { kind: "mine" }
  | { kind: "library" }
  | { kind: "collection"; id: string };

type Listed = Pick<RecipeCollection, "id" | "isDefault">;

export function landingTab(collections: readonly Listed[]): RecipeTab {
  const preferred = collections.find((collection) => collection.isDefault);
  if (preferred) {
    return { kind: "collection", id: preferred.id };
  }
  return collections.length > 0 ? { kind: "gallery" } : { kind: "mine" };
}

export function tabParam(tab: RecipeTab): string {
  switch (tab.kind) {
    case "gallery":
      return "tabs";
    case "mine":
    case "library":
      return tab.kind;
    case "collection":
      return tab.id;
  }
}

export function tabFromParam(param: string | null, collections: readonly Listed[]): RecipeTab {
  if (param === "mine" || param === "library") {
    return { kind: param };
  }
  if (param === "tabs" && collections.length > 0) {
    return { kind: "gallery" };
  }
  if (param && collections.some((collection) => collection.id === param)) {
    return { kind: "collection", id: param };
  }
  return landingTab(collections);
}

export function sameTab(a: RecipeTab, b: RecipeTab): boolean {
  return tabParam(a) === tabParam(b);
}

export const MAX_NAME = 60;
export const MAX_TAGS = 12;
export const MAX_TAG = 30;

export type CoverDraft =
  | { kind: "none" }
  | { kind: "saved"; pictureId: string }
  | { kind: "new"; prepared: PreparedPicture };

export type TabDraft = {
  name: string;
  tags: string[];
  recipeIds: string[];
  cover: CoverDraft;
  openByDefault: boolean;
};

export function tabDraftOf(collection?: RecipeCollection): TabDraft {
  return {
    name: collection?.name ?? "",
    tags: collection?.tags ?? [],
    recipeIds: collection?.recipeIds ?? [],
    cover: collection?.pictureId ? { kind: "saved", pictureId: collection.pictureId } : { kind: "none" },
    openByDefault: collection?.isDefault ?? false,
  };
}

export function normalizeTag(raw: string): string | undefined {
  const tag = raw.trim().toLowerCase();
  return tag === "" ? undefined : tag;
}

export function toggleTag(draft: TabDraft, tag: string): TabDraft {
  return draft.tags.includes(tag)
    ? { ...draft, tags: draft.tags.filter((t) => t !== tag) }
    : { ...draft, tags: [...draft.tags, tag] };
}

export function togglePick(draft: TabDraft, recipeId: string): TabDraft {
  return draft.recipeIds.includes(recipeId)
    ? { ...draft, recipeIds: draft.recipeIds.filter((id) => id !== recipeId) }
    : { ...draft, recipeIds: [...draft.recipeIds, recipeId] };
}

export function tagChoices(recipes: readonly { tags: readonly string[] }[], chosen: readonly string[]): string[] {
  const tags = new Set(chosen);
  for (const recipe of recipes) {
    for (const tag of recipe.tags) {
      const normal = normalizeTag(tag);
      if (normal) {
        tags.add(normal);
      }
    }
  }
  return [...tags].sort((a, b) => a.localeCompare(b));
}

export function toCollectionInput(draft: TabDraft): { input: RecipeCollectionInput } | { errors: string[] } {
  const name = draft.name.trim();
  const errors: string[] = [];
  if (name === "") {
    errors.push("Give the tab a name.");
  } else if (name.length > MAX_NAME) {
    errors.push(`A tab's name can be at most ${MAX_NAME} characters.`);
  }
  if (draft.tags.length > MAX_TAGS) {
    errors.push(`A tab can gather by at most ${MAX_TAGS} tags.`);
  }
  const long = draft.tags.find((tag) => tag.length > MAX_TAG);
  if (long) {
    errors.push(`“${long}” is longer than ${MAX_TAG} characters.`);
  }
  return errors.length > 0 ? { errors } : { input: { name, tags: draft.tags, recipeIds: draft.recipeIds } };
}

export type TabApi = {
  create: (input: RecipeCollectionInput) => Promise<PictureResult<RecipeCollection>>;
  replace: (id: string, input: RecipeCollectionInput) => Promise<PictureResult<RecipeCollection>>;
  uploadCover: (id: string, blob: Blob) => Promise<PictureResult<RecipeCollection>>;
  removeCover: (id: string) => Promise<PictureResult<void>>;
  setDefault: (id: string, on: boolean) => Promise<PictureResult<void>>;
};

export type SaveProgress = { saved?: RecipeCollection; draft: TabDraft; error?: string };

export async function saveTab(api: TabApi, start: SaveProgress): Promise<SaveProgress> {
  const checked = toCollectionInput(start.draft);
  if ("errors" in checked) {
    return { ...start, error: checked.errors.join(" ") };
  }
  let draft = start.draft;
  const fields = start.saved ? await api.replace(start.saved.id, checked.input) : await api.create(checked.input);
  if (!fields.ok) {
    return { saved: start.saved, draft, error: fields.message };
  }
  let saved = fields.value;

  if (draft.cover.kind === "new") {
    const uploaded = await api.uploadCover(saved.id, draft.cover.prepared.blob);
    if (!uploaded.ok) {
      return { saved, draft, error: uploaded.message };
    }
    saved = uploaded.value;
    draft = { ...draft, cover: saved.pictureId ? { kind: "saved", pictureId: saved.pictureId } : { kind: "none" } };
  } else if (draft.cover.kind === "none" && saved.pictureId) {
    const removed = await api.removeCover(saved.id);
    if (!removed.ok && removed.status !== 404) {
      return { saved, draft, error: removed.message };
    }
    saved = { ...saved, pictureId: undefined };
  }

  if (draft.openByDefault !== saved.isDefault) {
    const chosen = await api.setDefault(saved.id, draft.openByDefault);
    if (!chosen.ok) {
      return { saved, draft, error: chosen.message };
    }
    saved = { ...saved, isDefault: draft.openByDefault };
  }
  return { saved, draft };
}
