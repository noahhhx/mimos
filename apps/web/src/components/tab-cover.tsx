"use client";

import { useEffect, useState } from "react";

import { getRecipeCollectionPicture } from "@mimos/api-client";

import { BlobImage } from "@/components/recipe-pictures";
import { apiClient } from "@/lib/api";

/**
 * A tab's saved cover (ADR-0036). Like a recipe picture it needs the
 * bearer token, so it comes through the API client, and its path carries
 * the picture id, so the browser may cache it for good.
 */
export function TabCoverImage({
  collectionId,
  pictureId,
  alt,
}: {
  collectionId: string;
  pictureId: string;
  alt: string;
}) {
  const [blob, setBlob] = useState<Blob>();
  useEffect(() => {
    const controller = new AbortController();
    getRecipeCollectionPicture({
      client: apiClient,
      path: { collectionId, pictureId },
      parseAs: "blob",
      signal: controller.signal,
    })
      .then((result) => {
        if (result.data instanceof Blob) {
          setBlob(result.data);
        }
      })
      .catch(() => {
        // Aborted on unmount, or the network failed: the frame stays empty.
      });
    return () => controller.abort();
  }, [collectionId, pictureId]);
  return blob ? <BlobImage blob={blob} alt={alt} /> : null;
}
