package io.github.noahhhx.mimos.recipes.recipe;

/** The image formats a recipe picture may have, read from its bytes (ADR-0023). */
public enum PictureFormat {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private final String mediaType;

    PictureFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }
}
