package io.github.noahhhx.mimos.recipes.recipe;

/** A picture's bytes with the format to serve them as. */
public record PictureContent(PictureFormat format, byte[] bytes) {}
