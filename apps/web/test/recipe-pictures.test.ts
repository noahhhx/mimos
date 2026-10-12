import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  MAX_PICTURES,
  addPicks,
  draftOf,
  fitWithin,
  hasChanges,
  pendingChanges,
  removeSlot,
  syncPictures,
  type PictureApi,
  type PictureDraft,
  type PictureResult,
} from "../src/lib/recipe-pictures.ts";

const saved = (...ids: string[]) => draftOf({ pictures: ids.map((id) => ({ id })) });
const pick = (key: string) => ({ key, prepared: { blob: new Blob([key], { type: "image/jpeg" }) } });
const ids = (draft: PictureDraft) =>
  draft.slots.map((slot) => (slot.kind === "saved" ? slot.picture.id : `new:${slot.key}`));

/** A fake API that records each call and answers from a script; uploads get `${prefix}-n` ids in call order. */
function fakeApi(
  answers: { remove?: Record<string, PictureResult<void>>; failUpload?: Set<string> } = {},
  prefix = "uploaded",
) {
  const calls: string[] = [];
  let next = 0;
  const api: PictureApi = {
    remove: async (recipeId, pictureId) => {
      calls.push(`remove ${recipeId} ${pictureId}`);
      return answers.remove?.[pictureId] ?? { ok: true, value: undefined };
    },
    upload: async (recipeId, blob) => {
      const key = await blob.text();
      calls.push(`upload ${recipeId} ${key}`);
      if (answers.failUpload?.has(key)) {
        return { ok: false, status: 400, message: `${key} is not a picture` };
      }
      next += 1;
      return { ok: true, value: { id: `${prefix}-${next}` } };
    },
  };
  return { api, calls };
}

describe("fitWithin", () => {
  it("shrinks the long edge to the limit, keeping the ratio", () => {
    assert.deepEqual(fitWithin(4000, 3000), { width: 1600, height: 1200 });
    assert.deepEqual(fitWithin(3000, 4000), { width: 1200, height: 1600 });
  });

  it("never enlarges a small picture", () => {
    assert.deepEqual(fitWithin(800, 600), { width: 800, height: 600 });
    assert.deepEqual(fitWithin(1600, 900), { width: 1600, height: 900 });
  });

  it("keeps at least one pixel on a very thin picture", () => {
    assert.deepEqual(fitWithin(10000, 2), { width: 1600, height: 1 });
  });
});

describe("the picture draft", () => {
  it("starts from the recipe's pictures, or nothing for a new recipe", () => {
    assert.deepEqual(ids(saved("a", "b")), ["a", "b"]);
    assert.deepEqual(draftOf(), { slots: [], removed: [] });
  });

  it("appends picks after the saved pictures and leaves out what passes the limit", () => {
    const { draft, leftOut } = addPicks(saved("a", "b"), [pick("x"), pick("y")]);
    assert.deepEqual(ids(draft), ["a", "b", "new:x", "new:y"]);
    assert.equal(leftOut, 0);

    const full = addPicks(
      saved("a"),
      Array.from({ length: MAX_PICTURES }, (_, i) => pick(`p${i}`)),
    );
    assert.equal(full.draft.slots.length, MAX_PICTURES);
    assert.equal(full.leftOut, 1);
    assert.equal(addPicks(full.draft, [pick("late")]).leftOut, 1);
  });

  it("remembers a removed saved picture and simply drops a removed pick", () => {
    const draft = addPicks(saved("a", "b"), [pick("x")]).draft;
    const withoutSaved = removeSlot(draft, 0);
    assert.deepEqual(ids(withoutSaved), ["b", "new:x"]);
    assert.deepEqual(withoutSaved.removed, ["a"]);

    const withoutPick = removeSlot(draft, 2);
    assert.deepEqual(ids(withoutPick), ["a", "b"]);
    assert.deepEqual(withoutPick.removed, []);
    assert.equal(hasChanges(withoutPick), false);
  });

  it("sends removals before uploads, and uploads in slot order", () => {
    const draft = removeSlot(addPicks(saved("a", "b"), [pick("x"), pick("y")]).draft, 1);
    const pending = pendingChanges(draft);
    assert.deepEqual(pending.remove, ["b"]);
    assert.deepEqual(
      pending.upload.map((upload) => upload.key),
      ["x", "y"],
    );
  });
});

describe("syncPictures", () => {
  it("removes, then uploads in order, and ends with only saved pictures", async () => {
    const draft = removeSlot(addPicks(saved("a", "b"), [pick("x"), pick("y")]).draft, 0);
    const { api, calls } = fakeApi();

    const result = await syncPictures(api, "r1", draft);

    assert.equal(result.error, undefined);
    assert.deepEqual(calls, ["remove r1 a", "upload r1 x", "upload r1 y"]);
    assert.deepEqual(ids(result.draft), ["b", "uploaded-1", "uploaded-2"]);
    assert.equal(hasChanges(result.draft), false);
  });

  it("stops at a failed upload, and saving again sends only the rest", async () => {
    const draft = addPicks(saved("a"), [pick("x"), pick("bad"), pick("y")]).draft;
    const first = fakeApi({ failUpload: new Set(["bad"]) });

    const stopped = await syncPictures(first.api, "r1", draft);

    assert.equal(stopped.error, "bad is not a picture");
    assert.deepEqual(first.calls, ["upload r1 x", "upload r1 bad"]);
    assert.deepEqual(ids(stopped.draft), ["a", "uploaded-1", "new:bad", "new:y"]);

    const retry = fakeApi({}, "retried");
    const finished = await syncPictures(retry.api, "r1", removeSlot(stopped.draft, 2));
    assert.deepEqual(retry.calls, ["upload r1 y"]);
    assert.deepEqual(ids(finished.draft), ["a", "uploaded-1", "retried-1"]);
    assert.equal(hasChanges(finished.draft), false);
  });

  it("counts a removal that finds the picture gone as done", async () => {
    const draft = removeSlot(saved("gone", "b"), 0);
    const { api } = fakeApi({ remove: { gone: { ok: false, status: 404, message: "picture not found" } } });

    const result = await syncPictures(api, "r1", draft);

    assert.equal(result.error, undefined);
    assert.deepEqual(result.draft.removed, []);
  });

  it("stops before any upload when a removal fails", async () => {
    const draft = addPicks(removeSlot(saved("a"), 0), [pick("x")]).draft;
    const { api, calls } = fakeApi({ remove: { a: { ok: false, status: 500, message: "try again" } } });

    const result = await syncPictures(api, "r1", draft);

    assert.equal(result.error, "try again");
    assert.deepEqual(calls, ["remove r1 a"]);
    assert.deepEqual(pendingChanges(result.draft).remove, ["a"]);
    assert.deepEqual(
      pendingChanges(result.draft).upload.map((upload) => upload.key),
      ["x"],
    );
  });
});
