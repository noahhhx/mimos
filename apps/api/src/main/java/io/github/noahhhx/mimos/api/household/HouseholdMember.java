package io.github.noahhhx.mimos.api.household;

import java.util.UUID;

/** Someone in a household, by profile id and the name the household sees. */
public record HouseholdMember(UUID profileId, String displayName) {}
