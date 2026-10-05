package io.github.noahhhx.mimos.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Manifest parsing: the homepage becomes a link in users' browsers, so only http(s) gets through. */
class PluginManifestTests {

    private static PluginManifest withHomepage(String homepageJson) {
        String json = """
                {"schema":"mimos.plugin.manifest/v1","id":"stub-plugin","name":"Stub Plugin",\
                "version":"1.0.0","apiVersions":["1"],"capabilities":["plan-suggestions"],\
                "homepageUrl":%s}\
                """.formatted(homepageJson);
        return PluginManifest.fromJson(new ObjectMapper().readTree(json));
    }

    @Test
    void anHttpHomepageIsKept() {
        assertThat(withHomepage("\"https://example.org/mimos/stub\"").homepageUrl())
                .isEqualTo("https://example.org/mimos/stub");
        assertThat(withHomepage("\"http://stub.local:8080/\"").homepageUrl()).isEqualTo("http://stub.local:8080/");
    }

    @Test
    void anyOtherHomepageIsDroppedWithoutFailingTheManifest() {
        assertThat(withHomepage("\"javascript:alert(1)\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"JavaScript:alert(1)\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"data:text/html,<p>hi</p>\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"/relative/path\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"https://\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"not a url\"").homepageUrl()).isNull();
        assertThat(withHomepage("\"https://example.org/" + "x".repeat(2048) + "\"")
                        .homepageUrl())
                .isNull();
        assertThat(withHomepage("42").homepageUrl()).isNull();
    }
}
