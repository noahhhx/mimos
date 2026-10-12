"use client";

import { useRef, useState } from "react";

import { BlobImage, RecipePictureImage } from "@/components/recipe-pictures";
import { preparePicture } from "@/lib/prepare-picture";
import { addPicks, MAX_PICTURES, removeSlot, type PictureDraft, type PreparedPicture } from "@/lib/recipe-pictures";

/**
 * The recipe form's Pictures section (ADR-0023): saved pictures and new
 * picks in one row, each with Remove. A pick is shrunk as soon as it is
 * chosen, so a file that is not a picture says so at once. Nothing reaches
 * the API until the form is saved.
 */
export function PicturesField({
  recipeId,
  title,
  draft,
  onChange,
  disabled,
}: {
  recipeId: string | undefined;
  title: string;
  draft: PictureDraft;
  onChange: (next: PictureDraft) => void;
  disabled: boolean;
}) {
  const [preparing, setPreparing] = useState(false);
  const [problems, setProblems] = useState<string[]>([]);
  const nextKey = useRef(0);
  const busy = disabled || preparing;
  const full = draft.slots.length >= MAX_PICTURES;

  const pick = async (files: File[]) => {
    setPreparing(true);
    const picks: { key: string; prepared: PreparedPicture }[] = [];
    const failed: string[] = [];
    for (const file of files) {
      try {
        nextKey.current += 1;
        picks.push({ key: `pick-${nextKey.current}`, prepared: await preparePicture(file) });
      } catch (error) {
        failed.push(error instanceof Error ? error.message : `${file.name} can't be added.`);
      }
    }
    // Remove and Add are disabled while preparing, so `draft` is still current.
    const added = addPicks(draft, picks);
    if (added.leftOut > 0) {
      failed.push(`A recipe can have ${MAX_PICTURES} pictures, so ${added.leftOut} of them were left out.`);
    }
    setProblems(failed);
    setPreparing(false);
    onChange(added.draft);
  };

  return (
    <>
      <h2>Pictures</h2>
      <p className="muted">
        Up to {MAX_PICTURES}, shown above the method in this order. They are shrunk before they are sent, and saved
        with the recipe.
      </p>
      {draft.slots.length > 0 && (
        <ul className="picture-row compact" aria-label="Pictures">
          {draft.slots.map((slot, index) => {
            const alt = `${title || "Recipe"}, picture ${index + 1} of ${draft.slots.length}`;
            return (
              <li className="picture-slot" key={slot.kind === "saved" ? slot.picture.id : slot.key}>
                <div className="picture">
                  {slot.kind === "new" ? (
                    <BlobImage blob={slot.prepared.blob} alt={alt} />
                  ) : (
                    recipeId && <RecipePictureImage recipeId={recipeId} pictureId={slot.picture.id} alt={alt} />
                  )}
                </div>
                <p className="picture-slot-actions">
                  {slot.kind === "new" && <span className="muted">Not saved yet</span>}
                  <button
                    type="button"
                    className="button secondary small"
                    onClick={() => onChange(removeSlot(draft, index))}
                    disabled={busy}
                    aria-label={`Remove picture ${index + 1}`}
                  >
                    Remove
                  </button>
                </p>
              </li>
            );
          })}
        </ul>
      )}
      {problems.length > 0 && (
        <div className="card error" role="alert">
          {problems.map((problem) => (
            <p key={problem}>{problem}</p>
          ))}
        </div>
      )}
      <p>
        <label className={`button secondary file-button${busy || full ? " disabled" : ""}`}>
          {preparing ? "Shrinking…" : "Add pictures"}
          <input
            type="file"
            accept="image/*"
            multiple
            className="visually-hidden"
            disabled={busy || full}
            onChange={(event) => {
              const files = Array.from(event.target.files ?? []);
              event.target.value = "";
              if (files.length > 0) {
                void pick(files);
              }
            }}
          />
        </label>
        {full && <span className="muted"> A recipe can have {MAX_PICTURES} pictures.</span>}
      </p>
    </>
  );
}
