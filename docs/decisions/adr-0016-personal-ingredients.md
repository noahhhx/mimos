# ADR-0016: Personal ingredients and the ingredient search

- Status: Accepted
- Date: 2026-10-05
- Supersedes: none (extends ADR-0015)

## Context

ADR-0015 linked recipe lines to a shared, seeded ingredient catalog
through a "Counts as" dropdown beside each line's name. Two problems
showed up at once. A dropdown of every ingredient does not scale and
reads as a second name. And an ingredient missing from the shared list
could not count at all, so the first recipe with something unusual
fell back to typed nutrition.

## Decision

### Users add their own ingredients

A catalog entry may have an owner. An owned entry is that user's own:
listed, linkable, editable, and deletable only by them, and invisible to
everyone else (another user's slug is a 400 on a recipe line and a 404
on the ingredient). Shared entries stay seeded and read-only (403).
Personal recipes are private, so the ingredients they are made of are
too. A shared list that anyone can write would need review.

- `POST /api/v1/ingredients` creates one from a name, a basis, and all
  four nutrition values; the slug is made from the name plus a random
  suffix (`dragon-fruit-k3f9q2`), so it stays readable and unique.
- `PUT /api/v1/ingredients/{slug}` replaces its name and nutrition; the
  slug stays, so every recipe that uses it is recalculated on read.
- `DELETE /api/v1/ingredients/{slug}` removes it; lines that used it stay
  and stop counting (`ON DELETE SET NULL`).
- `GET /api/v1/ingredients` lists shared entries and the caller's own,
  marked by `isShared`.

### The name is the search

Each ingredient line has one "Ingredient" field that searches the
ingredients the user can see as they type, by word start, best first.
Picking one links the line. When nothing fits, the last option adds what
was typed as a new ingredient of the user's own, inline in the recipe
form, with its basis guessed from the line's unit. A line still links
itself when its name names an ingredient, and keeps its link while the
name still names it ("garlic clove" to "garlic cloves"). A "Don't count
it" button unlinks a line and stops it relinking.

### Prep notes leave the name

A line gains an optional `note` ("minced", "to serve"), shown after the
name. The search then matches what the ingredient is, not how it is cut,
and shopping-list lines that differ only by prep merge. The library seed
moves its prep notes out of its names.

### Export

Export format version 2 also carries the user's own ingredients
(`ingredients`, keyed by slug within the document) and each line's
`note`. Version 2 was not yet released when ADR-0015 introduced it, so
this extends it rather than adding version 3. Import creates the
ingredients first under new slugs and points the lines at them.

## Consequences

- A recipe always has a way to count every ingredient, at the cost of
  the user reading a food label once per new ingredient.
- Duplicates are possible: two users may each add "dragon fruit", and
  one user may add an ingredient the shared list later gains. Search
  shows both; nothing merges them.
- Contract 0.8.0. The new endpoints are MCP tools, so agents can add a
  missing ingredient before linking a line to it.
