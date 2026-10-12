# ADR-0023: Recipe pictures

- Status: Accepted
- Date: 2026-10-12
- Builds on: [ADR-0003](adr-0003-openapi-contract-pipeline.md) (contract-first API),
  [ADR-0011](adr-0011-account-export-import.md) (export format),
  [ADR-0014](adr-0014-agent-access-mcp.md) (agent tools),
  [ADR-0019](adr-0019-households.md) (who owns a recipe)
- Changes: the AGENTS.md "Object storage" open decision, which deferred
  recipe images to an S3-compatible store

## Context

The owner wants pictures on a recipe: several per recipe, shown at the
top of the main column of the recipe page, before the description, in
one row of equal height that scrolls sideways inside its own box.

AGENTS.md left object storage open: "S3-compatible API only, MinIO in
local compose", to be decided when recipe images arrive. MinIO's
community edition has since been archived upstream, and a second storage
service would be one more thing every self-hoster runs and backs up. The
owner decided:

1. Picture bytes live in Postgres, behind an internal seam, so an
   S3-compatible store can replace them later without changing the HTTP
   API.
2. The account export includes pictures, as base64, in a new format
   version.
3. The layout above: one row, one height, sideways scrolling, snapping on
   phones.

## Decision

### Storage

Two tables (V16). `recipe_picture` holds what a picture is: its id, its
recipe, its position, and its format (`JPEG`, `PNG`, or `WEBP`). It is
the single record that a picture exists. `recipe_picture_data` holds the
bytes, keyed by picture id, with `storage external` because the three
formats are compressed already. Both cascade from the recipe, so a
household join, which moves recipes, needs no change, and deleting a
recipe deletes its pictures.

`core-recipes` owns both. `PictureStore` is the seam: `put` and `get`
bytes by picture id. It knows nothing of recipes, owners, formats, or
limits. `PostgresPictureStore` is its only implementation and the only
code that touches `recipe_picture_data`.

The seam has no delete. Bytes without a row are garbage. Postgres removes
them by cascade, but a store outside the database cannot, because a
recipe delete cascades past any Java code. A future S3-compatible store
therefore owes a sweep that removes objects with no row. That sweep is
part of building such a store, not of this seam. `put` runs after the row
insert, in the same transaction, so a non-transactional store can leave
an orphan on rollback, which the same sweep removes.

### Rules

`RecipePictureService` owns every picture rule:

- A picture is a JPEG, PNG, or WebP, read from its magic bytes
  (`ImageSniffer`), never from a client's Content-Type. The server never
  decodes an image.
- At most 2 MB per picture and 8 per recipe (`MAX_PICTURE_BYTES`,
  `MAX_PICTURES`). The limits live once, in Java: the multipart limits
  are a `MultipartConfigElement` built from `MAX_PICTURE_BYTES`, and the
  web mirrors both numbers.
- Ownership comes from `RecipeService.requireOwned` (package-private):
  another household's recipe is a 404, a library recipe a 403. Any member
  of the owning household may add or remove a picture.
- An add locks the recipe's row first, so concurrent adds never pass the
  limit or share a position. Positions only grow; order is upload order,
  and there is no reorder.

### API

Pictures are a sub-resource, never part of `RecipeInput`, so replacing a
recipe keeps them and a new recipe gets them after it exists:

- `POST /api/v1/recipes/{recipeId}/pictures`, multipart part `file`:
  201 with the picture's id. A file over 2 MB is a 413 (Spring's own
  problem-details); anything else invalid is a 400.
- `GET /api/v1/recipes/{recipeId}/pictures/{pictureId}`: the bytes, as the
  stored format, with `Cache-Control: max-age=31536000, private,
  immutable` and `X-Content-Type-Options: nosniff`. A picture never
  changes, so a year of private caching is safe.
- `DELETE` the same path: 204.
- `RecipeDetail.pictures` lists ids in display order. `RecipeApiMapper.toDetail`
  takes the pictures, so no caller can forget them; the public library
  passes none.

The upload and the GET are `x-mcp: false`: the MCP tool parser cannot
describe a multipart body, and a binary response is useless to a JSON
tool. `deleteRecipePicture` stays a tool.

### Web

Images need the bearer token, so the web fetches each through the API
client as a Blob and shows it through an object URL (`BlobImage`, the one
place that makes and revokes them). The recipe form edits a
`PictureDraft` (saved and new slots, plus removed ids) with pure
transitions, and Save converges: fields first, then removals, then
uploads in order, updating the draft after each success. A failure keeps
the form open with the rest queued. A new recipe remembers its id after
the first Save, so a retry never creates it twice.

Before upload, the browser decodes each pick with its EXIF orientation,
shrinks it to 1600 px on the long side, flattens it on white, and
re-encodes it as JPEG at the first quality that fits 2 MB. This strips
metadata, GPS included, from web uploads. Uploads through the API are
stored exactly as sent.

### Export

Format version 6. Each exported recipe carries `pictures`, each a base64
`data` string. There is no content type: import reads it from the bytes,
through the same `RecipePictureService.add` as an upload, so a bad
picture fails the import with its path (`recipes[0].pictures[1]: …`) and
nothing is written. The upgrade step gives every version 5 recipe an
empty list.

## Alternatives considered

- **MinIO or another S3-compatible service now.** One more service for
  every self-hoster, and MinIO's community edition is archived. The seam
  keeps the move cheap when a deployment needs it.
- **The upload body as the raw image.** One request with no multipart
  framing, but its generator support and a streaming size cap were
  unverified. Multipart is the known-good path in both generators.
- **Pictures on the `Recipe` aggregate.** Every recipe read would load
  picture metadata, and `replace` would have to carry pictures it does
  not change.
- **Width and height in the API**, so a page can lay out before images
  load. About a hundred lines of header parsing to avoid a sideways
  reflow inside a fixed-height row; immutable caching makes revisits
  instant.
- **Reordering, and a household quota.** Not asked for. 8 × 2 MB bounds
  a recipe's cost.

## Consequences

- The `mimos` database, its backups, and account exports grow with
  pictures. The self-hosting guide says so.
- Export builds every picture in memory. At browser-shrunk sizes (about
  300 KB each) that is fine; a streaming exporter is the fix if
  households grow large.
- Chrome and Firefox on desktop cannot decode HEIC, so such a pick is
  refused with a message.
- Moving bytes to S3-compatible storage is one `PictureStore`
  implementation, one migration that copies and drops
  `recipe_picture_data`, and the orphan sweep. The HTTP API and the
  export stay as they are.
