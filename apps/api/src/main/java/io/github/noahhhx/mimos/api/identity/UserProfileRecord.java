package io.github.noahhhx.mimos.api.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * The per-user profile row (domain type; distinct from the API contract
 * model). {@code householdId} owns the user's shared data (ADR-0019).
 */
public record UserProfileRecord(
        UUID id, String subjectId, String displayName, UUID householdId, Instant createdAt, Instant updatedAt) {}
