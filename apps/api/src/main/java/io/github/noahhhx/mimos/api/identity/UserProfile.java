package io.github.noahhhx.mimos.api.identity;

import java.time.Instant;
import java.util.UUID;

/** The per-user profile row. */
public record UserProfile(UUID id, String subjectId, String displayName, Instant createdAt, Instant updatedAt) {}
