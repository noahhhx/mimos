package io.github.noahhhx.mimos.api.instance;

import static java.util.Objects.requireNonNull;

import org.openapitools.api.InstanceApi;
import org.openapitools.model.Instance;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Names this server's version and the oldest app it supports, for apps to check when they connect. */
@RestController
public class PublicInstanceController implements InstanceApi {

    /**
     * The oldest app version this server supports. Raised only by a change an older app cannot survive;
     * `/api/v1` is additive-only (ADR-0029), so that should be rare.
     */
    static final String MIN_APP_VERSION = "0.0.0";

    private final String version;

    public PublicInstanceController(BuildProperties build) {
        this.version = release(requireNonNull(build.getVersion(), "build-info records the project version"));
    }

    @Override
    public ResponseEntity<Instance> getInstance() {
        return ResponseEntity.ok(new Instance(version, MIN_APP_VERSION));
    }

    /** The contract promises MAJOR.MINOR.PATCH, so a qualifier such as -SNAPSHOT or +build metadata is dropped. */
    static String release(String buildVersion) {
        int suffix = buildVersion.length();
        for (char marker : new char[] {'-', '+'}) {
            int at = buildVersion.indexOf(marker);
            if (at >= 0) {
                suffix = Math.min(suffix, at);
            }
        }
        return buildVersion.substring(0, suffix);
    }
}
