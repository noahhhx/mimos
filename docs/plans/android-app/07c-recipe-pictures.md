# 07c. Recipe pictures

**Depends on:** 07b

A recipe's pictures, shown and added, as the web's `recipe-pictures.tsx`.
ADR-0023. Split from item 07.

## Build

- Show a recipe's pictures in its detail (`getRecipePicture`, read with
  `.response.readRawBytes()`; "Found along the way").
- Take a photo or pick one (Android photo picker, no storage permission),
  shrink to 1600 px and re-encode as JPEG without metadata (the rule in
  `apps/web/src/lib/recipe-pictures.ts`), save fields, then removals, then
  uploads, as the web does. Shrinking sits behind the image service
  interface (ADR-0029).

## Checks

- `commonTest` for the save order and the shrink rule.
- Flow: `recipe-picture` (a fixture image pushed to the emulator).
- Gates: mobile.

## Decided while building

- The rules are a port of the web's `recipe-pictures.ts` in
  `recipes/pictures/PictureDraft.kt` (`PictureDraft`, `syncPictures`), tested
  in `PictureDraftTest`; the limits and `fitWithin` read
  `libraries/test-cases/recipe-pictures.json`, as the web's tests now do.
- The image service is `PictureShrinker` (commonMain), `AndroidPictureShrinker`
  on Android: `ImageDecoder` from API 28, which applies the EXIF orientation,
  and `BitmapFactory` with the platform `ExifInterface` on API 26 and 27 (lint's
  `ExifInterface` check is suppressed there rather than adding the androidx
  copy as a dependency). A power-of-two sample size keeps a 12-megapixel photo
  out of memory, then the bitmap is scaled to `fitWithin` exactly, drawn on
  white, and compressed at the first of 85, 75, 60, 45 that fits 2 MB.
- Picking is `rememberPicturePicker` (expect/actual): the androidx photo
  picker (`PickMultipleVisualMedia`, no permission) and "Take a photo" through
  the phone's camera app (`TakePicture`) into one cache file behind a
  `FileProvider`, deleted once shrunk. The button shows only when a camera app
  answers `IMAGE_CAPTURE` (a `<queries>` entry). More picks than room are cut
  by the form with the web's message.
- `RecipePictures` saves pictures for the form and fetches them for showing,
  keeping up to 24 MB of image bytes in memory, least recently shown out
  first, cleared on sign-out. A picture never changes, so a recipe shown again
  asks nothing.
- After its first save a new recipe's form edits that recipe, so a picture
  that fails keeps the form open and saving again replaces it rather than
  creating a second one, as the web does.
- The detail shows the row between the title and the description, 240 dp high,
  each picture at most 85% of the row's width, snapping as it scrolls.
- The flow pushes `flows/assets/recipe-picture.jpg` (2400 × 1800, EXIF
  orientation "rotate 90") with Maestro's `addMedia`. Checked by hand once
  against the server: the upload was 1200 × 1600, upright, with no EXIF.
