package io.github.noahhhx.mimos.api.identity;

import java.time.Instant;
import java.util.UUID;

/** The per-user profile row (domain type; distinct from the API contract model). */
public record UserProfileRecord(UUID id, String subjectId, String displayName, Instant createdAt, Instant updatedAt) {}
