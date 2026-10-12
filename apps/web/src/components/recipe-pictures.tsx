"use client";

import { useEffect, useState } from "react";

import { getRecipePicture, type RecipeDetail } from "@mimos/api-client";

import { apiClient } from "@/lib/api";

/**
 * The recipe's pictures, one row at one height, scrolling sideways in its
 * own box (ADR-0023). A recipe without pictures, every library recipe
 * among them, renders nothing and fetches nothing.
 */
export function RecipePictures({ recipe }: { recipe: RecipeDetail }) {
  if (recipe.pictures.length === 0) {
    return null;
  }
  return (
    <ul className="picture-row" aria-label="Pictures" tabIndex={0}>
      {recipe.pictures.map((picture, index) => (
        <li className="picture" key={picture.id}>
          <RecipePictureImage
            recipeId={recipe.id}
            pictureId={picture.id}
            alt={`${recipe.title}, picture ${index + 1} of ${recipe.pictures.length}`}
          />
        </li>
      ))}
    </ul>
  );
}

/**
 * A saved picture's image. It needs the bearer token, so it is fetched
 * through the API client rather than by an <img src>; the browser caches
 * it for a year, since a picture never changes.
 */
export function RecipePictureImage({ recipeId, pictureId, alt }: { recipeId: string; pictureId: string; alt: string }) {
  const [blob, setBlob] = useState<Blob>();
  useEffect(() => {
    const controller = new AbortController();
    getRecipePicture({ client: apiClient, path: { recipeId, pictureId }, parseAs: "blob", signal: controller.signal })
      .then((result) => {
        if (result.data instanceof Blob) {
          setBlob(result.data);
        }
      })
      .catch(() => {
        // Aborted on unmount, or the network failed: the frame stays empty.
      });
    return () => controller.abort();
  }, [recipeId, pictureId]);
  return blob ? <BlobImage blob={blob} alt={alt} /> : null;
}

/**
 * An <img> for a Blob. The one place that makes image object URLs: each
 * lives exactly as long as this component shows its Blob.
 */
export function BlobImage({ blob, alt }: { blob: Blob; alt: string }) {
  const [url, setUrl] = useState<string>();
  useEffect(() => {
    const next = URL.createObjectURL(blob);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [blob]);
  return url ? <img src={url} alt={alt} /> : null;
}
