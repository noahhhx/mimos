/**
 * The plugin runtime (ADR-0006): instance-config registry, the outbound
 * extension-API client, and suggestion-card validation. Pull-only in v1 —
 * Mimos calls plugins, plugins never call back. External API types never
 * cross this boundary; the context is assembled from core's public
 * interfaces and translated here.
 */
@org.jspecify.annotations.NullMarked
package io.github.noahhhx.mimos.plugins;
