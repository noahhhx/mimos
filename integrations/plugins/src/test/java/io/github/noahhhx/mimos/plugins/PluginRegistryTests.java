package io.github.noahhhx.mimos.plugins;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Registry semantics (ADR-0006): static misconfiguration fails startup,
 * unreachability never does, and manifests are retried lazily so a plugin
 * that starts after the API is still picked up. The plugins a user can
 * turn on are the usable ones (ADR-0013).
 */
class PluginRegistryTests {

    private static final String MANIFEST = """
            {"schema":"mimos.plugin.manifest/v1","id":"stub-plugin","name":"Stub Plugin",\
            "version":"1.0.0","apiVersions":["1"],"capabilities":["plan-suggestions"]}\
            """;

    @Test
    @Timeout(10)
    void duplicateIdsFailStartup() throws IOException {
        try (StubPluginServer first = StubPluginServer.start(MANIFEST, "{\"suggestions\":[]}");
                StubPluginServer second = StubPluginServer.start(MANIFEST, "{\"suggestions\":[]}")) {
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(List.of(
                    new PluginRegistration("stub-plugin", first.url(), null, Duration.ofMillis(500)),
                    new PluginRegistration(null, second.url(), null, Duration.ofMillis(500)))));
            assertThatThrownBy(registry::warmUp)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("duplicate plugin id 'stub-plugin'");
        }
    }

    @Test
    @Timeout(10)
    void configuredIdMismatchFailsStartup() throws IOException {
        try (StubPluginServer stub = StubPluginServer.start(MANIFEST, "{\"suggestions\":[]}")) {
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(
                    List.of(new PluginRegistration("other-plugin", stub.url(), null, Duration.ofMillis(500)))));
            assertThatThrownBy(registry::warmUp)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("configured as 'other-plugin'");
        }
    }

    @Test
    @Timeout(10)
    void unreachablePluginsNeverFailStartup() throws IOException {
        try (StubPluginServer stub = StubPluginServer.start(MANIFEST, "{\"suggestions\":[]}")) {
            // localhost:1 has no listener: connection refused, immediately.
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(List.of(
                    new PluginRegistration("stub-plugin", stub.url(), null, Duration.ofMillis(500)),
                    new PluginRegistration("dead", "http://localhost:1", null, Duration.ofMillis(500)))));
            assertThatCode(registry::warmUp).doesNotThrowAnyException();
            List<Plugin> capable = registry.pluginsWithCapability(PluginManifest.CAPABILITY_PLAN_SUGGESTIONS);
            assertThat(capable).hasSize(1);
            assertThat(requireNonNull(capable.get(0).manifest()).id()).isEqualTo("stub-plugin");
        }
    }

    @Test
    @Timeout(10)
    void unknownCapabilitiesAreIgnoredAndApiVersionsAreChecked() throws IOException {
        String manifest = """
                {"schema":"mimos.plugin.manifest/v1","id":"other","name":"Other",\
                "version":"1.0.0","apiVersions":["1"],\
                "capabilities":["something-else","plan-suggestions","far-future"]}\
                """;
        String oldApiVersions = """
                {"schema":"mimos.plugin.manifest/v1","id":"old","name":"Old",\
                "version":"1.0.0","apiVersions":["2"],"capabilities":["plan-suggestions"]}\
                """;
        try (StubPluginServer other = StubPluginServer.start(manifest, "{\"suggestions\":[]}");
                StubPluginServer old = StubPluginServer.start(oldApiVersions, "{\"suggestions\":[]}")) {
            // "other" declares the capability plus unknown ones (ignored
            // with a warning) and speaks v1; "old" does not speak v1.
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(List.of(
                    new PluginRegistration(null, other.url(), null, Duration.ofMillis(500)),
                    new PluginRegistration(null, old.url(), null, Duration.ofMillis(500)))));
            registry.warmUp();
            List<Plugin> capable = registry.pluginsWithCapability(PluginManifest.CAPABILITY_PLAN_SUGGESTIONS);
            assertThat(capable)
                    .extracting(plugin -> requireNonNull(plugin.manifest()).id())
                    .containsExactly("other");
        }
    }

    @Test
    @Timeout(10)
    void availablePluginsAreTheOnesAUserCanTurnOn() throws IOException {
        String onlyUnknown = """
                {"schema":"mimos.plugin.manifest/v1","id":"future","name":"Future",\
                "version":"1.0.0","apiVersions":["1"],"capabilities":["far-future"]}\
                """;
        String onlyWeekPanel = """
                {"schema":"mimos.plugin.manifest/v1","id":"panel-only","name":"Panel Only",\
                "version":"1.0.0","apiVersions":["1"],"capabilities":["week-panel"]}\
                """;
        try (StubPluginServer stub = StubPluginServer.start(MANIFEST, "{\"suggestions\":[]}");
                StubPluginServer future = StubPluginServer.start(onlyUnknown, "{\"suggestions\":[]}");
                StubPluginServer panelOnly = StubPluginServer.start(onlyWeekPanel, "{\"suggestions\":[]}")) {
            // Listed (ADR-0013): resolved, speaking v1, calling a capability
            // this Mimos knows. Not listed: unreachable, or nothing to call.
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(List.of(
                    new PluginRegistration(null, "http://localhost:1", null, Duration.ofMillis(500)),
                    new PluginRegistration(null, future.url(), null, Duration.ofMillis(500)),
                    new PluginRegistration(null, stub.url(), null, Duration.ofMillis(500)),
                    new PluginRegistration(null, panelOnly.url(), null, Duration.ofMillis(500)))));
            registry.warmUp();
            assertThat(registry.availablePlugins())
                    .extracting(PluginManifest::id)
                    .containsExactly("stub-plugin", "panel-only");
        }
    }

    @Test
    @Timeout(10)
    void aPluginStartingAfterTheApiIsPickedUpLazily() throws IOException {
        // Created (bound) but not serving: connections hang until timeout —
        // exactly a plugin container that has not finished starting.
        try (StubPluginServer stub = StubPluginServer.create(MANIFEST, "{\"suggestions\":[]}")) {
            PluginRegistry registry = new PluginRegistry(new PluginsProperties(
                    List.of(new PluginRegistration(null, stub.url(), null, Duration.ofMillis(500)))));
            // Warm-up finds nothing (unreachable) — never fatal.
            assertThatCode(registry::warmUp).doesNotThrowAnyException();
            assertThat(registry.pluginsWithCapability(PluginManifest.CAPABILITY_PLAN_SUGGESTIONS))
                    .isEmpty();
            // The plugin comes up later; the next request resolves it.
            stub.start();
            List<Plugin> capable = registry.pluginsWithCapability(PluginManifest.CAPABILITY_PLAN_SUGGESTIONS);
            assertThat(capable).hasSize(1);
            assertThat(requireNonNull(capable.get(0).manifest()).id()).isEqualTo("stub-plugin");
        }
    }

    @Test
    void invalidRegistrationsFailBinding() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new PluginRegistration(null, "ftp://example.org", null, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http(s)");
        assertThatThrownBy(() -> new PluginRegistration(null, "http://localhost:8080", null, Duration.ofMillis(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }
}
