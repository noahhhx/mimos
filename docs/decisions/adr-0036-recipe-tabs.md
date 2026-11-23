# ADR-0036: Recipe tabs

- Status: Accepted
- Date: 2026-11-23
- Builds on: [ADR-0011](adr-0011-account-export-import.md) (export format),
  [ADR-0019](adr-0019-households.md) (who owns what),
  [ADR-0023](adr-0023-recipe-pictures.md) (picture rules)

## Context

The Recipes page has two tabs, "My recipes" and "Library". The owner wants
people to add their own tabs. A tab holds chosen recipes, or recipes
"belonging to tabs that they add", which this ADR reads as tags. Recipes
opens on a gallery of tile cards for the tabs, and each tab can have a
picture. A person can make one tab their default, so Recipes opens on that
tab and skips the gallery. A household with no tabs sees the page as it is
today.

## Decision

- **A tab is a recipe collection, and it is household data.** Code,
  tables, and the API say "recipe collection" (`RecipeCollection`,
  `/api/v1/recipe-collections`). What users see says "tab". Everyone in a
  household sees and edits the same tabs. The household joins in
  ADR-0019 treat tabs like meal plans. On a join from a household of
  one, its tabs are deleted, not moved: names could clash with the new
  household's, and tabs only arrange recipes that come along anyway. A
  shared household keeps its tabs when a member leaves.
- **Membership is a union.** A recipe is in a tab when it is picked into
  the tab or it has any of the tab's tags. This covers the household's
  personal recipes and the library. `RecipeCollectionRepository.memberIds`
  is the only statement of this rule, as one SQL query. Members are
  listed through `RecipeService.findAmong`: personal recipes first, newest
  first, then library recipes by title. This matches how the two existing
  lists are ordered. The `q` search is the same as in `listMyRecipes`.
  Tags gather recipes again on every read and are never stored as picks.
- **Rules (`RecipeCollectionService`).** The name is trimmed, 1 to 60
  characters, and unique in the household ignoring case. A unique index on
  `(household_id, lower(name))` enforces this, and a clash is a **409**
  (`CollectionNameTakenException`, "Name taken"). 409 is chosen over 400
  because the input is valid and conflicts with existing data. Tags are
  trimmed, lowercased, and distinct: at most 12, each at most 30
  characters, like recipe tags. Tags match recipe tags ignoring case,
  because the server stores recipe tags as typed. Picks are distinct, at
  most 500, and each must be a household recipe or a library recipe.
  Anything else is a 400 that names the id, and is never silently
  dropped. A tab may be empty. Another household's tab is a 404 on every
  operation. When a picked recipe is deleted, it drops out of the tab
  through the foreign key cascade.
- **The default is per person.** Choosing a landing tab is a viewing
  preference, so it does not change another member's choice.
  `recipe_collection_default` has one row per profile, so a person has at
  most one default. Core takes the profile id as an opaque `personId`, as
  core-fueling does. Reads join the default to the person's current
  household, so a default left behind in a household the person left is
  ignored. Deleting a tab clears it as anyone's default (cascade). Setting
  and clearing are idempotent. Clearing a tab that is not the caller's
  default is a 204 that changes nothing.
- **One picture per tab, stored inline.** `recipe_collection_picture`
  keeps the cover's id, format, and bytes in one row per tab
  (`storage external`). The `PictureStore` seam is not reused:
  `recipe_picture_data` has a foreign key to `recipe_picture`, so it
  cannot hold a cover. A second store table would add a seam for one
  small row per tab that list queries never read. The rules are the
  recipe picture rules, in one place: `RecipePictureService.requirePicture`
  (JPEG, PNG, or WebP by magic bytes, at most 2 MB). The 2 MB multipart
  limit gives the same 413. Replacing a cover gives it a new id, and the
  image path carries the id (`/picture/{pictureId}`), so the image is
  cached as immutable, like a recipe picture. An old id is a 404.
  Removing a cover that is not there is a 204.
- **API.** `listRecipeCollections`, `createRecipeCollection` (201),
  `getRecipeCollection`, `replaceRecipeCollection`, which keeps the
  picture and every default, `deleteRecipeCollection`,
  `listRecipeCollectionRecipes` (`q`), `setDefaultRecipeCollection` and
  `clearDefaultRecipeCollection` (204), `setRecipeCollectionPicture`
  (multipart `file`, answers the tab), `deleteRecipeCollectionPicture`
  (204), and `getRecipeCollectionPicture`. The upload and the image are
  `x-mcp: false`, like recipe pictures, and every other operation is an
  agent tool. `RecipeCollection` carries `id`, `name`, `tags`,
  `recipeIds`, `pictureId?`, `isDefault` (for the caller), `recipeCount`,
  and `createdAt`. `RecipeCollectionInput` is `name`, `tags`, and
  `recipeIds`. Tabs are listed oldest first, and there is no reordering.
- **The landing rule, which every client follows.** When Recipes opens
  with no tab chosen, the client applies these rules in order:
  1. If the caller's default tab still exists, open it.
  2. If the household has any tab, open the gallery.
  3. Otherwise, open "My recipes", exactly as before tabs existed.

  The operation descriptions tell agents the same rule.
- **Export version 10.** `recipeCollections` holds each tab's name,
  tags, `recipeIds` (own recipes by document id), `librarySlugs`, an
  optional base64 `picture`, `isDefault` (present only on the exporter's
  default), and `createdAt`. Import restores tabs through the service, so
  the import checks them like an API call. A picture goes through the
  upload's rules, and a second default is a 400. A library slug that
  this instance lacks is left out of the tab, with a warning. A tab
  counts as data when import checks that an account is empty.
  `ImportReport.recipeCollections` gives the count. The version 9 step
  adds an empty section, and `export/v10.json` is the frozen fixture.

## Consequences

- Plugins never see tabs, so the plugin contract is unchanged.
- A tab's `recipeCount` runs the membership query once per tab. That is
  cheap for a household's handful of tabs. A grouped count is the fix if
  that changes.
- Moving pictures to S3-compatible storage (ADR-0032) now also means
  copying the bytes out of `recipe_collection_picture`. The API and the
  export stay the same.
- A person who rejoins a household gets back the default they left there,
  if that tab still exists.
