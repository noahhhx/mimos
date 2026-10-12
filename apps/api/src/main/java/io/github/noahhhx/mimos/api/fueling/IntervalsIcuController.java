package io.github.noahhhx.mimos.api.fueling;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnection;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnections;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.IntervalsIcuApi;
import org.openapitools.model.IntervalsIcuConnectionInput;
import org.openapitools.model.IntervalsIcuConnectionStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The caller's intervals.icu connection (ADR-0022); every route is a 404 while the feature is off. */
@RestController
public class IntervalsIcuController implements IntervalsIcuApi {

    private final ObjectProvider<IntervalsIcuConnections> connections;
    private final CurrentUserService currentUser;

    public IntervalsIcuController(ObjectProvider<IntervalsIcuConnections> connections, CurrentUserService currentUser) {
        this.connections = connections;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.IntervalsIcuConnection> getMyIntervalsIcu() {
        IntervalsIcuConnections service = active();
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(service.connection(profileId)
                .map(IntervalsIcuController::toApi)
                .orElseGet(() -> new org.openapitools.model.IntervalsIcuConnection()
                        .status(IntervalsIcuConnectionStatus.DISCONNECTED)));
    }

    @Override
    public ResponseEntity<org.openapitools.model.IntervalsIcuConnection> connectMyIntervalsIcu(
            IntervalsIcuConnectionInput input) {
        IntervalsIcuConnections service = active();
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(toApi(service.connect(
                profileId, present(input.getAthleteId(), "athleteId"), present(input.getApiKey(), "apiKey"))));
    }

    @Override
    public ResponseEntity<Void> disconnectMyIntervalsIcu() {
        IntervalsIcuConnections service = active();
        service.disconnect(currentUser.requireProfile().id());
        return ResponseEntity.noContent().build();
    }

    private IntervalsIcuConnections active() {
        IntervalsIcuConnections service = connections.getIfAvailable();
        if (service == null) {
            throw new NoSuchElementException("intervals.icu is not available on this instance");
        }
        return service;
    }

    private static org.openapitools.model.IntervalsIcuConnection toApi(IntervalsIcuConnection connection) {
        Instant lastSyncAt = connection.lastSyncAt();
        return new org.openapitools.model.IntervalsIcuConnection()
                .status(IntervalsIcuConnectionStatus.valueOf(connection.status().name()))
                .athleteId(connection.athleteId())
                .lastSyncAt(lastSyncAt == null ? null : OffsetDateTime.ofInstant(lastSyncAt, ZoneOffset.UTC))
                .lastError(connection.lastError());
    }

    private static <T> T present(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
