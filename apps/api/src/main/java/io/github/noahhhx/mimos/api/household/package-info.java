/**
 * Households (ADR-0019): membership, invites, and the rules for joining
 * and leaving. Owns the {@code household} and {@code household_invite}
 * tables and {@code user_profile.household_id}; what a household owns in
 * other modules changes only through those modules' public services.
 */
@NullMarked
package io.github.noahhhx.mimos.api.household;

import org.jspecify.annotations.NullMarked;
