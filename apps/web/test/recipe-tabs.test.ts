import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { RecipeCollection, RecipeCollectionInput } from "@mimos/api-client";

import {
  landingTab,
  normalizeTag,
  pickedFirst,
  saveTab,
  tabDraftOf,
  tabFromParam,
  tabParam,
  tagChoices,
  toCollectionInput,
  toggleTag,
  togglePick,
  type TabApi,
  type TabDraft,
} from "../src/lib/recipe-tabs.ts";

const tab = (id: string, isDefault = false) => ({ id, isDefault });

describe("landingTab", () => {
  it("opens My recipes when the household has no tabs", () => {
    assert.deepEqual(landingTab([]), { kind: "mine" });
  });

  it("opens the gallery when there are tabs and none is the default", () => {
    assert.deepEqual(landingTab([tab("a"), tab("b")]), { kind: "gallery" });
  });

  it("opens the caller's default tab", () => {
    assert.deepEqual(landingTab([tab("a"), tab("b", true)]), { kind: "collection", id: "b" });
  });
});

describe("pickedFirst", () => {
  const recipes = [{ id: "a" }, { id: "b" }, { id: "c" }, { id: "d" }];

  it("lists the picked recipes first, each group in its original order", () => {
    assert.deepEqual(
      pickedFirst(recipes, ["d", "b"]).map((r) => r.id),
      ["b", "d", "a", "c"],
    );
  });

  it("keeps the order when nothing is picked", () => {
    assert.deepEqual(
      pickedFirst(recipes, []).map((r) => r.id),
      ["a", "b", "c", "d"],
    );
  });

  it("ignores picks that are not in the list", () => {
    assert.deepEqual(
      pickedFirst(recipes, ["gone", "c"]).map((r) => r.id),
      ["c", "a", "b", "d"],
    );
  });
});

describe("tabFromParam", () => {
  const tabs = [tab("a"), tab("b", true)];

  it("applies the landing rule without a param", () => {
    assert.deepEqual(tabFromParam(null, tabs), { kind: "collection", id: "b" });
  });

  it("reads the built-in tabs and the gallery", () => {
    assert.deepEqual(tabFromParam("mine", tabs), { kind: "mine" });
    assert.deepEqual(tabFromParam("library", tabs), { kind: "library" });
    assert.deepEqual(tabFromParam("tabs", tabs), { kind: "gallery" });
  });

  it("opens a tab that exists", () => {
    assert.deepEqual(tabFromParam("a", tabs), { kind: "collection", id: "a" });
  });

  it("falls back to the landing rule for a deleted tab", () => {
    assert.deepEqual(tabFromParam("gone", tabs), { kind: "collection", id: "b" });
    assert.deepEqual(tabFromParam("gone", []), { kind: "mine" });
  });

  it("does not open an empty gallery", () => {
    assert.deepEqual(tabFromParam("tabs", []), { kind: "mine" });
  });

  it("round-trips through tabParam", () => {
    for (const each of [{ kind: "gallery" }, { kind: "mine" }, { kind: "library" }, { kind: "collection", id: "a" }]) {
      assert.deepEqual(tabFromParam(tabParam(each as never), tabs), each);
    }
  });
});

describe("draft edits", () => {
  const draft = tabDraftOf();

  it("normalizes a typed tag and ignores a blank one", () => {
    assert.equal(normalizeTag("  Weeknight "), "weeknight");
    assert.equal(normalizeTag("   "), undefined);
  });

  it("toggles tags and picks", () => {
    const tagged = toggleTag(toggleTag(draft, "soup"), "quick");
    assert.deepEqual(tagged.tags, ["soup", "quick"]);
    assert.deepEqual(toggleTag(tagged, "soup").tags, ["quick"]);
    assert.deepEqual(togglePick(togglePick(draft, "r1"), "r1").recipeIds, []);
  });

  it("offers every recipe tag once, lowercase, sorted, keeping chosen ones", () => {
    assert.deepEqual(tagChoices([{ tags: ["Soup", "quick"] }, { tags: ["soup"] }], ["zesty"]), [
      "quick",
      "soup",
      "zesty",
    ]);
  });

  it("requires a name and trims it", () => {
    assert.deepEqual(toCollectionInput({ ...draft, name: "  " }), { errors: ["Give the tab a name."] });
    assert.deepEqual(toCollectionInput({ ...draft, name: " Soups " }), {
      input: { name: "Soups", tags: [], recipeIds: [] },
    });
  });
});

const collection = (patch: Partial<RecipeCollection> = {}): RecipeCollection => ({
  id: "c1",
  name: "Soups",
  tags: [],
  recipeIds: [],
  isDefault: false,
  recipeCount: 0,
  createdAt: "2026-11-23T00:00:00Z",
  ...patch,
});

function fakeApi(failing: Set<string> = new Set(), stored: RecipeCollection = collection()) {
  const calls: string[] = [];
  const fail = { ok: false as const, status: 500, message: "boom" };
  const api: TabApi = {
    create: async (input: RecipeCollectionInput) => {
      calls.push("create");
      return failing.has("create") ? fail : { ok: true, value: collection({ name: input.name }) };
    },
    replace: async (id, input) => {
      calls.push(`replace ${id}`);
      return failing.has("replace") ? fail : { ok: true, value: { ...stored, id, name: input.name } };
    },
    uploadCover: async (id) => {
      calls.push(`upload ${id}`);
      return failing.has("upload") ? fail : { ok: true, value: collection({ id, pictureId: "p1" }) };
    },
    removeCover: async (id) => {
      calls.push(`remove ${id}`);
      return failing.has("remove") ? fail : { ok: true, value: undefined };
    },
    setDefault: async (id, on) => {
      calls.push(`default ${id} ${on}`);
      return failing.has("default") ? fail : { ok: true, value: undefined };
    },
  };
  return { api, calls };
}

describe("saveTab", () => {
  const blob = new Blob(["jpeg"]);
  const fresh: TabDraft = {
    ...tabDraftOf(),
    name: "Soups",
    cover: { kind: "new", prepared: { blob } },
    openByDefault: true,
  };

  it("creates, uploads the cover, then makes it the default", async () => {
    const { api, calls } = fakeApi();
    const done = await saveTab(api, { draft: fresh });
    assert.deepEqual(calls, ["create", "upload c1", "default c1 true"]);
    assert.equal(done.error, undefined);
    assert.equal(done.saved?.isDefault, true);
    assert.deepEqual(done.draft.cover, { kind: "saved", pictureId: "p1" });
  });

  it("retries a failed cover by replacing the saved tab, never creating it twice", async () => {
    const first = await saveTab(fakeApi(new Set(["upload"])).api, { draft: fresh });
    assert.equal(first.error, "boom");
    assert.equal(first.saved?.id, "c1");
    const { api, calls } = fakeApi();
    await saveTab(api, first);
    assert.deepEqual(calls, ["replace c1", "upload c1", "default c1 true"]);
  });

  it("removes a cover taken away, and counts one already gone as removed", async () => {
    const saved = collection({ pictureId: "p1" });
    const { api, calls } = fakeApi(new Set(), saved);
    api.removeCover = async (id) => {
      calls.push(`remove ${id}`);
      return { ok: false, status: 404, message: "gone" };
    };
    const done = await saveTab(api, { saved, draft: { ...tabDraftOf(saved), cover: { kind: "none" } } });
    assert.deepEqual(calls, ["replace c1", "remove c1"]);
    assert.equal(done.error, undefined);
  });

  it("clears the default only when it changed", async () => {
    const saved = collection({ isDefault: true });
    const { api, calls } = fakeApi(new Set(), saved);
    await saveTab(api, { saved, draft: { ...tabDraftOf(saved), openByDefault: false } });
    assert.deepEqual(calls, ["replace c1", "default c1 false"]);
  });

  it("sends nothing when the name is missing", async () => {
    const { api, calls } = fakeApi();
    const done = await saveTab(api, { draft: tabDraftOf() });
    assert.deepEqual(calls, []);
    assert.equal(done.error, "Give the tab a name.");
  });
});
