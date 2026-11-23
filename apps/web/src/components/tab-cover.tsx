"use client";

import { useEffect, useState } from "react";

import { getRecipeCollectionPicture } from "@mimos/api-client";

import { BlobImage } from "@/components/recipe-pictures";
import { apiClient } from "@/lib/api";

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
      .catch(() => {});
    return () => controller.abort();
  }, [collectionId, pictureId]);
  return blob ? <BlobImage blob={blob} alt={alt} /> : null;
}
