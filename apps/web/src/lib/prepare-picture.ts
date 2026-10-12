import { fitWithin, MAX_PICTURE_BYTES, QUALITY_STEPS, type PreparedPicture } from "./recipe-pictures.ts";

/**
 * Turns a picked file into an upload: decoded with its EXIF orientation
 * applied, shrunk to LONG_EDGE, flattened onto white (a transparent PNG has
 * no JPEG equivalent), and re-encoded as JPEG at the first quality that
 * fits MAX_PICTURE_BYTES. Re-encoding also drops the file's metadata, a
 * phone photo's location included (ADR-0023). Rejects with a message for
 * the cook when the browser cannot read the file as a picture.
 */
export async function preparePicture(file: File): Promise<PreparedPicture> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error(`${file.name} can't be read as a picture. Try a JPEG, PNG, or WebP.`);
  }
  const size = fitWithin(bitmap.width, bitmap.height);
  const canvas = document.createElement("canvas");
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext("2d");
  if (!context) {
    bitmap.close();
    throw new Error(`${file.name} can't be shrunk in this browser.`);
  }
  // The canvas starts transparent, which JPEG would turn black.
  context.fillStyle = "white";
  context.fillRect(0, 0, size.width, size.height);
  context.drawImage(bitmap, 0, 0, size.width, size.height);
  bitmap.close();
  for (const quality of QUALITY_STEPS) {
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (blob && blob.size <= MAX_PICTURE_BYTES) {
      return { blob };
    }
  }
  throw new Error(`${file.name} is too detailed to fit in 2 MB, even shrunk.`);
}
