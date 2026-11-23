"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import {
  clearDefaultRecipeCollection,
  createRecipeCollection,
  deleteRecipeCollection,
  deleteRecipeCollectionPicture,
  listLibraryRecipes,
  listMyRecipes,
  replaceRecipeCollection,
  setDefaultRecipeCollection,
  setRecipeCollectionPicture,
  type RecipeCollection,
  type RecipeSummary,
} from "@mimos/api-client";

import { BlobImage } from "@/components/recipe-pictures";
import { TabCoverImage } from "@/components/tab-cover";
import { apiClient } from "@/lib/api";
import { apiCall, problemDetail } from "@/lib/api-call";
import { preparePicture } from "@/lib/prepare-picture";
import {
  MAX_NAME,
  normalizeTag,
  pickedFirst,
  saveTab,
  tabDraftOf,
  tagChoices,
  togglePick,
  toggleTag,
  type SaveProgress,
  type TabApi,
} from "@/lib/recipe-tabs";

const tabApi: TabApi = {
  create: (input) =>
    apiCall(() => createRecipeCollection({ client: apiClient, body: input }), "The API rejected the tab."),
  replace: (collectionId, input) =>
    apiCall(
      () => replaceRecipeCollection({ client: apiClient, path: { collectionId }, body: input }),
      "The API rejected the tab.",
    ),
  uploadCover: (collectionId, blob) =>
    apiCall(
      () => setRecipeCollectionPicture({ client: apiClient, path: { collectionId }, body: { file: blob } }),
      "The picture could not be saved. Save again to retry.",
    ),
  removeCover: (collectionId) =>
    apiCall(
      () => deleteRecipeCollectionPicture({ client: apiClient, path: { collectionId } }),
      "The picture could not be removed. Save again to retry.",
    ),
  setDefault: (collectionId, on) =>
    apiCall(
      () =>
        on
          ? setDefaultRecipeCollection({ client: apiClient, path: { collectionId } })
          : clearDefaultRecipeCollection({ client: apiClient, path: { collectionId } }),
      "The default could not be changed. Save again to retry.",
    ),
};

export function RecipeTabForm({ initial }: { initial?: RecipeCollection }) {
  const router = useRouter();
  const [progress, setProgress] = useState<SaveProgress>(() => ({ saved: initial, draft: tabDraftOf(initial) }));
  const [recipes, setRecipes] = useState<RecipeSummary[] | null>(null);
  const [newTag, setNewTag] = useState("");
  const [pickQuery, setPickQuery] = useState("");
  const [preparing, setPreparing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const { saved, draft } = progress;
  const busy = saving || preparing;
  const setDraft = (next: typeof draft) => setProgress((current) => ({ ...current, draft: next }));

  useEffect(() => {
    void Promise.all([listMyRecipes({ client: apiClient }), listLibraryRecipes({ client: apiClient })]).then(
      ([mine, library]) => {
        setRecipes(pickedFirst([...(mine.data ?? []), ...(library.data ?? [])], initial?.recipeIds ?? []));
      },
    );
  }, [initial?.recipeIds]);

  const tags = tagChoices(recipes ?? [], draft.tags);
  const needle = pickQuery.trim().toLowerCase();
  const pickable = (recipes ?? []).filter((recipe) => needle === "" || recipe.title.toLowerCase().includes(needle));

  const addTag = () => {
    const tag = normalizeTag(newTag);
    if (tag && !draft.tags.includes(tag)) {
      setDraft(toggleTag(draft, tag));
    }
    setNewTag("");
  };

  const pickCover = async (file: File) => {
    setPreparing(true);
    try {
      const prepared = await preparePicture(file);
      setDraft({ ...draft, cover: { kind: "new", prepared } });
      setErrors([]);
    } catch (error) {
      setErrors([error instanceof Error ? error.message : `${file.name} can't be added.`]);
    }
    setPreparing(false);
  };

  const save = async () => {
    setSaving(true);
    setErrors([]);
    const done = await saveTab(tabApi, progress);
    setProgress(done);
    setSaving(false);
    if (done.error || !done.saved) {
      setErrors([done.error ?? "The API rejected the tab."]);
      return;
    }
    router.push(`/app/recipes?tab=${done.saved.id}`);
  };

  const remove = async () => {
    if (!saved || !confirm(`Delete the tab “${saved.name}”? Its recipes stay where they are.`)) {
      return;
    }
    setSaving(true);
    const result = await deleteRecipeCollection({ client: apiClient, path: { collectionId: saved.id } });
    setSaving(false);
    if (result.error) {
      setErrors([problemDetail(result, "The tab could not be deleted.")]);
      return;
    }
    router.push("/app/recipes");
  };

  const coverAlt = `${draft.name.trim() || "Tab"} cover`;

  return (
    <form
      className="card form tab-form"
      onSubmit={(e) => {
        e.preventDefault();
        void save();
      }}
    >
      {errors.length > 0 && (
        <div className="card error" role="alert">
          {errors.map((error) => (
            <p key={error}>{error}</p>
          ))}
        </div>
      )}

      <label>
        Name
        <input
          value={draft.name}
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          required
          maxLength={MAX_NAME}
          placeholder="Weeknight dinners"
        />
      </label>

      <h2>Picture</h2>
      <p className="muted">The tab's tile in the gallery. It is shrunk before it is sent.</p>
      {draft.cover.kind !== "none" && (
        <div className="tab-form-cover">
          {draft.cover.kind === "new" ? (
            <BlobImage blob={draft.cover.prepared.blob} alt={coverAlt} />
          ) : (
            saved && <TabCoverImage collectionId={saved.id} pictureId={draft.cover.pictureId} alt={coverAlt} />
          )}
        </div>
      )}
      <p className="button-row">
        <label className={`button secondary file-button${busy ? " disabled" : ""}`}>
          {preparing ? "Shrinking…" : draft.cover.kind === "none" ? "Choose picture" : "Replace picture"}
          <input
            type="file"
            accept="image/*"
            className="visually-hidden"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (file) {
                void pickCover(file);
              }
            }}
          />
        </label>
        {draft.cover.kind !== "none" && (
          <button
            type="button"
            className="button secondary"
            onClick={() => setDraft({ ...draft, cover: { kind: "none" } })}
            disabled={busy}
          >
            Remove picture
          </button>
        )}
        {draft.cover.kind === "new" && <span className="muted">Not saved yet</span>}
      </p>

      <h2>Tags</h2>
      <p className="muted">Every recipe with any of these tags is in the tab, including ones you add later.</p>
      {tags.length > 0 && (
        // biome-ignore lint/a11y/useSemanticElements: a <fieldset> has the same group role but brings its own border, padding, and form semantics.
        <div className="button-row" role="group" aria-label="Tags">
          {tags.map((tag) => {
            const active = draft.tags.includes(tag);
            return (
              <button
                key={tag}
                type="button"
                className={active ? "button small tag-filter" : "button secondary small tag-filter"}
                aria-pressed={active}
                onClick={() => setDraft(toggleTag(draft, tag))}
              >
                {tag}
              </button>
            );
          })}
        </div>
      )}
      <div className="tab-form-new-tag">
        <label>
          New tag
          <input
            value={newTag}
            onChange={(e) => setNewTag(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                addTag();
              }
            }}
            maxLength={30}
            placeholder="weeknight"
          />
        </label>
        <button
          type="button"
          className="button secondary"
          onClick={addTag}
          disabled={normalizeTag(newTag) === undefined}
        >
          Add tag
        </button>
      </div>

      <h2>Recipes</h2>
      <p className="muted">Pick recipes to keep in the tab whatever their tags. {draft.recipeIds.length} picked.</p>
      <input
        type="search"
        value={pickQuery}
        onChange={(e) => setPickQuery(e.target.value)}
        placeholder="Search your recipes and the library…"
        aria-label="Search recipes to pick"
      />
      {recipes === null ? (
        <p className="muted">Loading…</p>
      ) : (
        <ul className="pick-list" aria-label="Recipes to pick">
          {pickable.map((recipe) => (
            <li key={recipe.id}>
              <label>
                <input
                  type="checkbox"
                  checked={draft.recipeIds.includes(recipe.id)}
                  onChange={() => setDraft(togglePick(draft, recipe.id))}
                />
                {recipe.title}
              </label>
              <span className="muted">{recipe.isLibrary ? "Library" : "Yours"}</span>
            </li>
          ))}
          {pickable.length === 0 && (
            <li>
              <span className="muted">No recipe matches “{pickQuery}”.</span>
            </li>
          )}
        </ul>
      )}

      <div className="choices">
        <label>
          <input
            type="checkbox"
            checked={draft.openByDefault}
            onChange={(e) => setDraft({ ...draft, openByDefault: e.target.checked })}
          />
          Open Recipes on this tab
        </label>
      </div>

      <p className="actions">
        <button type="submit" className="button" disabled={busy}>
          {saving ? "Saving…" : saved ? "Save changes" : "Create tab"}
        </button>
        <button
          type="button"
          className="button secondary"
          onClick={() => router.push(saved ? `/app/recipes?tab=${saved.id}` : "/app/recipes")}
          disabled={saving}
        >
          Cancel
        </button>
        {saved && (
          <button type="button" className="button danger" onClick={() => void remove()} disabled={saving}>
            Delete tab
          </button>
        )}
      </p>
    </form>
  );
}
