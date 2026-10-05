package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/**
 * A plugin available on the instance, as one user sees it (ADR-0013).
 *
 * @param id the plugin's manifest id
 * @param name the plugin's manifest name
 * @param homepageUrl the plugin's documentation or project page
 * @param enabled whether this user turned the plugin on; plugins start off
 */
public record UserPlugin(String id, String name, @Nullable String homepageUrl, boolean enabled) {}
