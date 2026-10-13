package io.github.noahhhx.mimos.api.fueling;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.fueling.Sex;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnection;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnections;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuSync;
import io.github.noahhhx.mimos.intervalsicu.ProfileHints;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.IntervalsIcuApi;
import org.openapitools.model.FuelSex;
import org.openapitools.model.IntervalsIcuConnectionInput;
import org.openapitools.model.IntervalsIcuConnectionStatus;
import org.openapitools.model.IntervalsIcuHints;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The caller's intervals.icu connection (ADR-0022); every route is a 404 while the feature is off. */
@RestController
public class IntervalsIcuController implements IntervalsIcuApi {

    private final ObjectProvider<IntervalsIcuConnections> connections;
    private final ObjectProvider<IntervalsIcuSync> sync;
    private final CurrentUserService currentUser;

    public IntervalsIcuController(
            ObjectProvider<IntervalsIcuConnections> connections,
            ObjectProvider<IntervalsIcuSync> sync,
            CurrentUserService currentUser) {
        this.connections = connections;
        this.sync = sync;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.IntervalsIcuConnection> getMyIntervalsIcu() {
        IntervalsIcuConnections service = active(connections);
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(toApi(service.connection(profileId)));
    }

    @Override
    public ResponseEntity<org.openapitools.model.IntervalsIcuConnection> syncMyIntervalsIcu() {
        IntervalsIcuSync service = active(sync);
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(toApi(service.sync(profileId)));
    }

    @Override
    public ResponseEntity<org.openapitools.model.IntervalsIcuConnection> connectMyIntervalsIcu(
            IntervalsIcuConnectionInput input) {
        IntervalsIcuConnections service = active(connections);
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(toApi(service.connect(
                profileId, present(input.getAthleteId(), "athleteId"), present(input.getApiKey(), "apiKey"))));
    }

    @Override
    public ResponseEntity<Void> disconnectMyIntervalsIcu() {
        IntervalsIcuConnections service = active(connections);
        service.disconnect(currentUser.requireProfile().id());
        return ResponseEntity.noContent().build();
    }

    private static <T> T active(ObjectProvider<T> provider) {
        T service = provider.getIfAvailable();
        if (service == null) {
            throw new NoSuchElementException("intervals.icu is not available on this instance");
        }
        return service;
    }

    private static org.openapitools.model.IntervalsIcuConnection toApi(Optional<IntervalsIcuConnection> connection) {
        return connection
                .map(IntervalsIcuController::toApi)
                .orElseGet(() -> new org.openapitools.model.IntervalsIcuConnection()
                        .status(IntervalsIcuConnectionStatus.DISCONNECTED));
    }

    private static org.openapitools.model.IntervalsIcuConnection toApi(IntervalsIcuConnection connection) {
        return new org.openapitools.model.IntervalsIcuConnection()
                .status(IntervalsIcuConnectionStatus.valueOf(connection.status().name()))
                .athleteId(connection.athleteId())
                .lastSyncAt(utc(connection.lastSyncAt()))
                .lastError(connection.lastError())
                .retryAt(utc(connection.retryAt()))
                .hints(toApi(connection.hints()));
    }

    private static IntervalsIcuHints toApi(ProfileHints hints) {
        Double weightKg = hints.weightKg();
        Double bodyFatPercent = hints.bodyFatPercent();
        Sex sex = hints.sex();
        return new IntervalsIcuHints()
                .weightKg(weightKg == null ? null : BigDecimal.valueOf(weightKg))
                .bodyFatPercent(bodyFatPercent == null ? null : BigDecimal.valueOf(bodyFatPercent))
                .sex(sex == null ? null : FuelSex.valueOf(sex.name()))
                .ftpWatts(hints.ftpWatts());
    }

    private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static <T> T present(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
