/**
 * Plugin-surface endpoints (ADR-0006): the suggestions endpoint is the one
 * core API addition of the plugin system, fanning out to registered
 * sidecar plugins through the runtime in integrations/plugins.
 */
@org.jspecify.annotations.NullMarked
package io.github.noahhhx.mimos.api.plugins;
